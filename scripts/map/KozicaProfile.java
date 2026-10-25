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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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

  record Trail(long id, String colour, String symbol, String ref, long[] ways) implements OsmRelationInfo {}

  record Boundary(long id, String kind) implements OsmRelationInfo {}

  private static final Comparator<Trail> TRAIL_ORDER =
      Comparator.comparingInt((Trail trail) -> colourRank(trail.colour())).thenComparingLong(Trail::id);

  private final Map<Long, long[]> wayEnds = new ConcurrentHashMap<>();
  private final Set<Long> trailWays = ConcurrentHashMap.newKeySet();

  public static void main(String[] args) throws IOException {
    var arguments = Arguments.fromArgs(args);
    var profile = new KozicaProfile();
    var trailWaysPath = arguments.file("trail_ways", "where to list every Trail's way ids, for the network", null);
    Planetiler.create(arguments)
        .setProfile(profile)
        .addOsmSource("osm", arguments.inputFile("osm_path", "the Region's cut", Path.of("region.osm.pbf")))
        .overwriteOutput(Path.of("region.pmtiles"))
        .run();
    if (trailWaysPath != null) {
      Files.write(trailWaysPath, new TreeSet<>(profile.trailWays).stream().map(String::valueOf).toList());
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
    if (relation.hasTag("type", "route") && relation.hasTag("route", "hiking")) {
      var trail = trailOf(relation);
      for (var way : trail.ways()) {
        trailWays.add(way);
      }
      return List.of(trail);
    }
    var kind = boundaryKind(relation);
    return kind == null ? null : List.of(new Boundary(relation.id(), kind));
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
    var trails = feature.relationInfo(Trail.class).stream()
        .map(OsmReader.RelationMember::relation)
        .distinct()
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
    if (reversed(feature.id(), trails.get(0), line)) {
      line = line.reverse();
    }
    for (var index = 0; index < trails.size(); index++) {
      var trail = trails.get(index);
      features.geometry("trails", line)
          .setMinZoom(10)
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
