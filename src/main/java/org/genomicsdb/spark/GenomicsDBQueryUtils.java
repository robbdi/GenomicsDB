package org.genomicsdb.spark;

import org.genomicsdb.model.Coordinates;
import org.genomicsdb.reader.GenomicsDBQuery.Pair;
import org.genomicsdb.model.GenomicsDBExportConfiguration;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GenomicsDBQueryUtils {

  public static JSONObject loadJson(String loader){
    JSONObject jsonObject = null;
    try {
      JSONParser parser = new JSONParser();
      jsonObject = (JSONObject)parser.parse(new FileReader(loader));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return jsonObject;
  }

  public static boolean check_configuration(String key, String value) {
    if (value == null || value.isEmpty()) {
      System.err.println("GenomicsDB Configuration does not contain value for key=" + key);
      return false;
    } else {
      return true;
    }
  }

  public static String getWorkspace(GenomicsDBExportConfiguration.ExportConfiguration exportConfig, JSONObject jsonObject) 
    throws RuntimeException{
      String workspace = (exportConfig.hasWorkspace())?exportConfig.getWorkspace():(String)jsonObject.get("workspace");
      if (!check_configuration("workspace", workspace)){
        throw new RuntimeException("GenomicsDBConfiguration is incomplete. Add workspace info and restart the operation");
      }
      return workspace;
    }

  public static String getVidMapping(GenomicsDBExportConfiguration.ExportConfiguration exportConfig, JSONObject jsonObject) 
    throws RuntimeException{
      String vidMapping = (exportConfig.hasVidMappingFile())?exportConfig.getVidMappingFile():(String)jsonObject.get("vid_mapping_file");
      if (!check_configuration("vid_mapping_file", vidMapping)){
        throw new RuntimeException("GenomicsDBConfiguration is incomplete. Add vid mapping info and restart the operation");
      }
      return vidMapping;
    }

  public static String getCallsetMapping(GenomicsDBExportConfiguration.ExportConfiguration exportConfig, JSONObject jsonObject) 
    throws RuntimeException{
      String callsetMapping = (exportConfig.hasCallsetMappingFile())?exportConfig.getCallsetMappingFile():(String)jsonObject.get("callset_mapping_file");
      if (!check_configuration("callset_mapping_file", callsetMapping)){
        throw new RuntimeException("GenomicsDBConfiguration is incomplete. Add callset mapping info and restart the operation");
      }
      return callsetMapping;
    }

  public static String getReferenceGenome(GenomicsDBExportConfiguration.ExportConfiguration exportConfig, JSONObject jsonObject) 
    throws RuntimeException{
      String referenceGenome = (exportConfig.hasReferenceGenome())?exportConfig.getReferenceGenome():(String)jsonObject.get("reference_genome");
      if (!check_configuration("reference_genome", referenceGenome)){
        throw new RuntimeException("GenomicsDBConfiguration is incomplete. Add reference genome info and restart the operation");
      }
      return referenceGenome;
    }

  public static Long getSegmentSize(GenomicsDBExportConfiguration.ExportConfiguration exportConfig, JSONObject jsonObject){ 
    Long segmentSize = 0L;
    segmentSize = (exportConfig.hasSegmentSize())?exportConfig.getSegmentSize():(Long)jsonObject.get("segment_size");
    return segmentSize;
  }
  
  public static List<Pair> ToColumnRangePairs(List<Coordinates.GenomicsDBColumnOrInterval> intervalLists) {
    List<Pair> intervalPairs = new ArrayList<>();
    for (Coordinates.GenomicsDBColumnOrInterval interval : intervalLists) {
      assert (interval.getColumnInterval().hasTiledbColumnInterval());
      Coordinates.TileDBColumnInterval tileDBColumnInterval =
        interval.getColumnInterval().getTiledbColumnInterval();
      //interval.getColumnInterval().getTiledbColumnInterval();
      assert (tileDBColumnInterval.hasBegin() && tileDBColumnInterval.hasEnd());
      intervalPairs.add(
        new Pair(tileDBColumnInterval.getBegin(), tileDBColumnInterval.getEnd()));
    }
    return intervalPairs;

  }

  public static List<Pair> ToRowRangePairs(List<GenomicsDBExportConfiguration.RowRange> rowRanges) {
    List<Pair> rangePairs = new ArrayList<>();
    for (GenomicsDBExportConfiguration.RowRange range : rowRanges) {
      assert (range.hasLow() && range.hasLow());
      rangePairs.add(new Pair(range.getLow(), range.getHigh()));
    }
    return rangePairs;
  }


}
