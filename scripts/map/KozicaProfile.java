import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class KozicaProfile implements Profile {

  private static final Set<String> WAYS =
      Set.of("path", "footway", "track", "steps", "unclassified", "tertiary", "secondary");

  record Trail(long id, String colour) implements OsmRelationInfo {}

  public static void main(String[] args) {
    var arguments = Arguments.fromArgs(args);
    Planetiler.create(arguments)
        .setProfile(new KozicaProfile())
        .addOsmSource("osm", arguments.inputFile("osm_path", "the Region's cut", Path.of("region.osm.pbf")))
        .overwriteOutput(Path.of("region.pmtiles"))
        .run();
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (!relation.hasTag("type", "route") || !relation.hasTag("route", "hiking")) {
      return null;
    }
    var colour = colourOf(relation);
    return colour == null ? null : List.of(new Trail(relation.id(), colour));
  }

  private static String colourOf(OsmElement.Relation relation) {
    var symbol = relation.getString("osmc:symbol");
    var colour = symbol != null ? symbol.split(":", 2)[0] : relation.getString("colour");
    if (colour == null || colour.isBlank()) {
      return null;
    }
    return colour.trim().toLowerCase(Locale.ROOT);
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon() && feature.hasTag("natural", "water")) {
      features.polygon("water");
    }
    if (feature.isPoint() && feature.hasTag("natural", "peak")) {
      features.point("peaks")
          .setMinZoom(11)
          .setAttr("name", feature.getString("name"))
          .setAttr("ele", feature.getString("ele"));
    }
    if (feature.canBeLine()) {
      ways(feature, features);
    }
  }

  private static void ways(SourceFeature feature, FeatureCollector features) {
    var colours = new TreeSet<String>();
    for (var trail : feature.relationInfo(Trail.class)) {
      colours.add(trail.relation().colour());
    }
    var highway = feature.getString("highway");
    if ((highway == null || !WAYS.contains(highway)) && colours.isEmpty()) {
      return;
    }
    var line = features.line("ways").setMinZoom(10).setAttr("highway", highway);
    if (!colours.isEmpty()) {
      line.setAttr("colour", colours.first()).setAttr("colours", String.join(";", colours));
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
