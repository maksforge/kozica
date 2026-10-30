import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.reader.osm.OsmSourceFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.LineString;

/** The Region schema of docs/phase-2.md § 3: its layer table is the filter. */
public class KozicaProfile implements Profile {

  private static final List<String> COLOURS = List.of("red", "blue", "green", "yellow", "black");
  private static final String NO_COLOUR = "other";
  private static final Map<String, String> COLOUR_ALIASES = Map.ofEntries(
      Map.entry("czerwony", "red"), Map.entry("#ff0000", "red"),
      Map.entry("niebieski", "blue"), Map.entry("#0000ff", "blue"),
      Map.entry("zielony", "green"), Map.entry("#00ff00", "green"),
      Map.entry("żółty", "yellow"), Map.entry("#ffff00", "yellow"),
      Map.entry("czarny", "black"), Map.entry("#000000", "black"));

  private static final Set<String> LAND_NATURAL =
      Set.of("wood", "scrub", "grassland", "heath", "scree", "bare_rock", "glacier");
  private static final Set<String> LAND_USE = Set.of("forest", "meadow", "residential");
  private static final Map<String, Integer> ROADS = Map.of(
      "motorway", 8, "trunk", 8, "primary", 9, "secondary", 10, "tertiary", 11,
      "unclassified", 12, "residential", 12, "track", 12, "service", 13);
  private static final Set<String> PATHS = Set.of("path", "footway", "steps", "bridleway");
  private static final Map<String, Integer> POIS = Map.of(
      "hut", 11, "peak", 11, "station", 11, "pass", 12, "shelter", 13,
      "viewpoint", 13, "parking", 13, "spring", 14, "stop", 14);
  private static final Map<String, Integer> PLACES = Map.of("town", 8, "village", 10, "hamlet", 12);

  private static final Set<String> REGIONAL = Set.of("rwn", "lwn");
  private static final Set<String> LONG_DISTANCE = Set.of("nwn", "iwn");
  private static final Set<String> ROLES =
      Set.of("forward", "backward", "alternative", "excursion", "approach", "branch");

  record Trail(long id, String colour, String symbol, String ref, long[] ways) implements OsmRelationInfo {}

  record Boundary(long id, String kind) implements OsmRelationInfo {}

  record Member(boolean way, long ref, String role) {}

  record Hiking(long id, String network, boolean marked, Trail marking, List<Member> members) {}

  private static final Comparator<Trail> TRAIL_ORDER =
      Comparator.comparingInt((Trail trail) -> colourRank(trail.colour())).thenComparingLong(Trail::id);

  private final Map<Long, long[]> wayEnds = new ConcurrentHashMap<>();
  private final Map<Long, Hiking> hiking = new ConcurrentHashMap<>();
  private volatile Membership membership;

  public static void main(String[] args) throws IOException {
    var arguments = Arguments.fromArgs(args);
    var profile = new KozicaProfile();
    var trailsPath = arguments.file("trails", "where to write every Trail's membership, for the network", null);
    Planetiler.create(arguments)
        .setProfile(profile)
        .addOsmSource("osm", arguments.inputFile("osm_path", "the Region's cut", Path.of("region.osm.pbf")))
        .overwriteOutput(Path.of("region.pmtiles"))
        .run();
    if (trailsPath != null) {
      Files.write(trailsPath, profile.membership().lines());
    }
  }

  @Override
  public void preprocessOsmWay(OsmElement.Way way) {
    var nodes = way.nodes();
    if (nodes.size() >= 2) {
      wayEnds.put(way.id(), new long[] {nodes.get(0), nodes.get(nodes.size() - 1)});
    }
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("route", "hiking") && relation.hasTag("type", "route", "superroute")) {
      var trail = trailOf(relation);
      var members = relation.members().stream()
          .filter(member -> member.type() != OsmElement.Type.NODE)
          .map(member -> new Member(member.type() == OsmElement.Type.WAY, member.ref(),
              ROLES.contains(member.role()) ? member.role() : ""))
          .toList();
      var marked = !relation.getString("osmc:symbol", "").isBlank() || !relation.getString("colour", "").isBlank();
      hiking.put(relation.id(), new Hiking(relation.id(), relation.getString("network", ""), marked, trail, members));
      return relation.hasTag("type", "route") ? List.of(trail) : null;
    }
    var kind = boundaryKind(relation);
    return kind == null ? null : List.of(new Boundary(relation.id(), kind));
  }

  private Membership membership() {
    var built = membership;
    if (built == null) {
      synchronized (this) {
        built = membership;
        if (built == null) {
          built = new Membership(Map.copyOf(hiking));
          membership = built;
        }
      }
    }
    return built;
  }

  /**
   * Which relations are Trails, and which Trails each relation's ways belong to (docs/phase-6c.md § 2):
   * a regional parent takes the members marked as it is as its pieces, a Long-distance Trail takes its
   * members with a role, and its own ways belong to no Trail.
   */
  static final class Membership {
    private final Map<Long, Hiking> hiking;
    private final Map<Long, List<Long>> parents = new HashMap<>();
    private final Map<Long, Set<Long>> owners = new HashMap<>();
    private final Map<Long, Trail> markings = new HashMap<>();

    Membership(Map<Long, Hiking> hiking) {
      this.hiking = hiking;
      for (var relation : new TreeMap<>(hiking).values()) {
        for (var piece : piecesOf(relation)) {
          parents.computeIfAbsent(piece.id(), id -> new ArrayList<>()).add(relation.id());
        }
      }
      // Filled here, before the tiles are built, because processFeature reads them from many threads.
      for (var id : hiking.keySet()) {
        for (var trail : owners(id)) {
          markings.computeIfAbsent(trail, this::markingFor);
        }
      }
    }

    private boolean longDistance(Hiking relation) {
      return LONG_DISTANCE.contains(relation.network())
          && relation.members().stream().anyMatch(member -> !member.way() && member.role().isEmpty());
    }

    private List<Hiking> memberRelations(Hiking relation) {
      return relation.members().stream()
          .filter(member -> !member.way() && hiking.containsKey(member.ref()))
          .map(member -> hiking.get(member.ref()))
          .toList();
    }

    private List<Hiking> piecesOf(Hiking relation) {
      if (longDistance(relation)) {
        return List.of();
      }
      if (LONG_DISTANCE.contains(relation.network())) {
        var withRole = relation.members().stream()
            .filter(member -> !member.way() && !member.role().isEmpty())
            .map(Member::ref)
            .collect(Collectors.toSet());
        return memberRelations(relation).stream().filter(member -> withRole.contains(member.id())).toList();
      }
      if (!REGIONAL.contains(relation.network())) {
        return List.of();
      }
      var marking = markingOf(relation);
      return marking == null ? List.of()
          : memberRelations(relation).stream().filter(member -> sameMarking(member.marking(), marking)).toList();
    }

    private Trail markingOf(Hiking relation) {
      if (relation.marked()) {
        return relation.marking();
      }
      var shared = memberRelations(relation).stream()
          .map(Hiking::marking)
          .map(marking -> List.of(marking.colour(), Objects.toString(marking.symbol(), "")))
          .distinct()
          .toList();
      return shared.size() == 1 ? memberRelations(relation).get(0).marking() : null;
    }

    private static boolean sameMarking(Trail a, Trail b) {
      return a.colour().equals(b.colour()) && Objects.equals(a.symbol(), b.symbol());
    }

    private boolean isTrail(Hiking relation) {
      return !parents.containsKey(relation.id())
          && !longDistance(relation)
          && (relation.members().stream().anyMatch(Member::way) || !piecesOf(relation).isEmpty());
    }

    Set<Long> ownersOf(long id) {
      return owners.getOrDefault(id, Set.of());
    }

    private Set<Long> owners(long id) {
      var found = owners.get(id);
      if (found == null) {
        found = new TreeSet<>();
        owners.put(id, found);
        var relation = hiking.get(id);
        if (relation != null && isTrail(relation)) {
          found.add(id);
        }
        for (var parent : parents.getOrDefault(id, List.of())) {
          found.addAll(owners(parent));
        }
      }
      return found;
    }

    Trail trail(long id) {
      return markings.get(id);
    }

    private Trail markingFor(long id) {
      var relation = hiking.get(id);
      var marking = markingOf(relation);
      var own = relation.marking();
      return marking == null ? own : new Trail(id, marking.colour(), marking.symbol(), own.ref(), own.ways());
    }

    private void members(Hiking relation, String role, Set<Long> seen, Map<Long, String> out) {
      if (!seen.add(relation.id())) {
        return;
      }
      var pieces = piecesOf(relation).stream().map(Hiking::id).collect(Collectors.toSet());
      for (var member : relation.members()) {
        var taken = role.isEmpty() ? member.role() : role;
        if (member.way()) {
          out.putIfAbsent(member.ref(), taken.isEmpty() ? String.valueOf(member.ref()) : member.ref() + "/" + taken);
        } else if (pieces.contains(member.ref())) {
          members(hiking.get(member.ref()), taken, seen, out);
        }
      }
    }

    /**
     * {@code trail <id> <colour> <symbol> <way>[/<role>] ...}, a piece's ways at its place and in its order,
     * then {@code long <id> <section> ...}: build-network.py's input, sorted so two builds write the same bytes.
     */
    List<String> lines() {
      var out = new ArrayList<String>();
      for (var relation : new TreeMap<>(hiking).values()) {
        if (isTrail(relation)) {
          var trail = trail(relation.id());
          var ways = new LinkedHashMap<Long, String>();
          members(relation, "", new HashSet<>(), ways);
          out.add(String.join("\t", "trail", String.valueOf(relation.id()), trail.colour(),
              Objects.toString(trail.symbol(), ""), String.join(" ", ways.values())));
        }
      }
      for (var relation : new TreeMap<>(hiking).values()) {
        if (longDistance(relation)) {
          var sections = relation.members().stream()
              .filter(member -> !member.way() && member.role().isEmpty() && hiking.containsKey(member.ref()))
              .map(member -> String.valueOf(member.ref()))
              .distinct()
              .toList();
          out.add(String.join("\t", "long", String.valueOf(relation.id()), String.join(" ", sections)));
        }
      }
      return out;
    }
  }

  private static Trail trailOf(OsmElement.Relation relation) {
    var osmcSymbol = relation.getString("osmc:symbol");
    var hasSymbol = osmcSymbol != null && !osmcSymbol.isBlank();
    var parts = hasSymbol ? osmcSymbol.split(":") : new String[0];
    var colour = hasSymbol
        ? colourOf(parts.length > 0 ? parts[0] : null)
        : colourOf(relation.getString("colour"));
    var symbol = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim().toLowerCase(Locale.ROOT) : null;
    var ways = relation.members().stream()
        .filter(member -> member.type() == OsmElement.Type.WAY)
        .mapToLong(OsmElement.Relation.Member::ref)
        .toArray();
    return new Trail(relation.id(), colour == null ? NO_COLOUR : colour, symbol, relation.getString("ref"), ways);
  }

  private static String colourOf(String value) {
    if (value == null) {
      return null;
    }
    var colour = value.trim().toLowerCase(Locale.ROOT);
    return COLOURS.contains(colour) ? colour : COLOUR_ALIASES.get(colour);
  }

  private static int colourRank(String colour) {
    var rank = COLOURS.indexOf(colour);
    return rank < 0 ? COLOURS.size() : rank;
  }

  // admin_level=2 alone also matches other things, so it is read with boundary=administrative.
  private static String boundaryKind(OsmElement element) {
    if (element.hasTag("boundary", "national_park")
        || (element.hasTag("boundary", "protected_area") && element.hasTag("protect_class", "2"))) {
      return "national_park";
    }
    if (element.hasTag("boundary", "administrative") && element.hasTag("admin_level", "2")) {
      return "country";
    }
    return null;
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon()) {
      land(feature, features);
    }
    water(feature, features);
    if (feature.canBeLine()) {
      boundaries(feature, features);
      roadsAndPaths(feature, features);
      trails(feature, features);
    }
    poi(feature, features);
    place(feature, features);
  }

  private static void land(SourceFeature feature, FeatureCollector features) {
    var natural = feature.getString("natural", "");
    var landuse = feature.getString("landuse", "");
    var kind = LAND_NATURAL.contains(natural) ? natural : LAND_USE.contains(landuse) ? landuse : null;
    if (kind != null) {
      features.polygon("land").setMinZoom(8).setAttr("kind", kind);
    }
  }

  private static void water(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon() && feature.hasTag("natural", "water")) {
      features.polygon("water").setMinZoom(8).setAttr("kind", "water");
    }
    if (feature.canBeLine() && feature.hasTag("waterway", "river", "stream")) {
      var kind = feature.getString("waterway");
      features.line("water").setMinZoom(kind.equals("river") ? 8 : 12).setAttr("kind", kind);
    }
  }

  private static void boundaries(SourceFeature feature, FeatureCollector features) {
    var kinds = new TreeSet<String>();
    for (var member : feature.relationInfo(Boundary.class)) {
      kinds.add(member.relation().kind());
    }
    if (feature instanceof OsmSourceFeature<?> osm
        && osm.originalElement() instanceof OsmElement.Way way) {
      var own = boundaryKind(way);
      if (own != null) {
        kinds.add(own);
      }
    }
    for (var kind : kinds) {
      features.line("boundaries").setMinZoom(6).setAttr("kind", kind);
    }
  }

  private static void roadsAndPaths(SourceFeature feature, FeatureCollector features) {
    var highway = feature.getString("highway");
    if (highway == null) {
      return;
    }
    var road = highway.endsWith("_link") ? highway.substring(0, highway.length() - "_link".length()) : highway;
    if (ROADS.containsKey(road)) {
      features.line("roads").setMinZoom(ROADS.get(road)).setAttr("kind", road);
    } else if (PATHS.contains(highway)) {
      features.line("paths").setMinZoom(12).setAttr("kind", highway).setAttr("sac_scale", feature.getString("sac_scale"));
    }
  }

  /** One feature per Trail per way, each way oriented by its reference relation (docs/phase-2.md § 3). */
  private void trails(SourceFeature feature, FeatureCollector features) {
    var relations = feature.relationInfo(Trail.class).stream()
        .map(OsmReader.RelationMember::relation)
        .distinct()
        .sorted(TRAIL_ORDER)
        .toList();
    if (relations.isEmpty()) {
      return;
    }
    var membership = membership();
    var trails = relations.stream()
        .flatMap(relation -> membership.ownersOf(relation.id()).stream())
        .distinct()
        .map(membership::trail)
        .sorted(TRAIL_ORDER)
        .toList();
    if (trails.isEmpty()) {
      return;
    }
    LineString line;
    try {
      line = (LineString) feature.line();
    } catch (GeometryException e) {
      e.log("trail way " + feature.id());
      return;
    }
    if (reversed(feature.id(), relations.get(0), line)) {
      line = line.reverse();
    }
    for (var index = 0; index < trails.size(); index++) {
      var trail = trails.get(index);
      features.geometry("trails", line)
          .setMinZoom(10)
          .setAttr("trail", trail.id())
          .setAttr("colour", trail.colour())
          .setAttr("symbol", trail.symbol())
          .setAttr("index", index)
          .setAttr("count", trails.size())
          .setAttr("ref", trail.ref());
    }
  }

  private boolean reversed(long way, Trail reference, LineString line) {
    var ends = wayEnds.get(way);
    var at = indexOf(reference.ways(), way);
    if (ends != null && at >= 0) {
      var previous = at > 0 ? wayEnds.get(reference.ways()[at - 1]) : null;
      if (touches(previous, ends[0])) {
        return false;
      }
      if (touches(previous, ends[1])) {
        return true;
      }
      var next = at + 1 < reference.ways().length ? wayEnds.get(reference.ways()[at + 1]) : null;
      if (touches(next, ends[1])) {
        return false;
      }
      if (touches(next, ends[0])) {
        return true;
      }
    }
    // World coordinates grow east in x and south in y: west first, or south first on a shared longitude.
    var start = line.getCoordinateN(0);
    var end = line.getCoordinateN(line.getNumPoints() - 1);
    return start.x != end.x ? start.x > end.x : start.y < end.y;
  }

  private static int indexOf(long[] ways, long way) {
    for (var i = 0; i < ways.length; i++) {
      if (ways[i] == way) {
        return i;
      }
    }
    return -1;
  }

  private static boolean touches(long[] ends, long node) {
    return ends != null && (ends[0] == node || ends[1] == node);
  }

  private static void poi(SourceFeature feature, FeatureCollector features) {
    var kind = poiKind(feature);
    if (kind == null) {
      return;
    }
    FeatureCollector.Feature point;
    if (feature.isPoint()) {
      point = features.point("pois");
    } else if (feature.canBePolygon()) {
      point = features.pointOnSurface("pois");
    } else {
      return;
    }
    point.setMinZoom(POIS.get(kind))
        .setAttr("kind", kind)
        .setAttr("name", feature.getString("name"))
        .setAttr("ele", feature.getString("ele"));
  }

  private static String poiKind(SourceFeature feature) {
    if (feature.hasTag("tourism", "alpine_hut")) {
      return "hut";
    }
    if (feature.hasTag("tourism", "wilderness_hut")
        || (feature.hasTag("amenity", "shelter") && !feature.hasTag("shelter_type", "public_transport"))) {
      return "shelter";
    }
    if (feature.hasTag("natural", "peak")) {
      return "peak";
    }
    if (feature.hasTag("natural", "saddle") || feature.hasTag("mountain_pass", "yes")) {
      return "pass";
    }
    if (feature.hasTag("natural", "spring")) {
      return "spring";
    }
    if (feature.hasTag("tourism", "viewpoint")) {
      return "viewpoint";
    }
    if (feature.hasTag("amenity", "parking")) {
      return "parking";
    }
    if (feature.hasTag("highway", "bus_stop")) {
      return "stop";
    }
    if (feature.hasTag("railway", "station", "halt")) {
      return "station";
    }
    return null;
  }

  private static void place(SourceFeature feature, FeatureCollector features) {
    var kind = feature.getString("place");
    if (kind == null || !PLACES.containsKey(kind)) {
      return;
    }
    var point = feature.isPoint() ? features.point("places")
        : feature.canBePolygon() ? features.pointOnSurface("places") : null;
    if (point != null) {
      point.setMinZoom(PLACES.get(kind)).setAttr("kind", kind).setAttr("name", feature.getString("name"));
    }
  }

  @Override
  public String name() {
    return "Kozica";
  }

  @Override
  public String attribution() {
    return OSM_ATTRIBUTION;
  }
}
