import java.io.*;
import java.util.*;

import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import javax.json.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Main {

  private static final String input = "release/crawl";
  private static final String output = "annotations.xml";
  private static BufferedReader in;
  private static final int nWorkers = 28;

  public static void main(String[] args) throws IOException {

    System.out.println("Determining size of dataset");
    int count = countLines(input);
    int remaining = count;

    System.out.println("Processing annotations...");
    System.out.println("Finished processing annotations");
  }

  private static void exit(Exception e) {
    System.err.println(e.getMessage());
    System.err.println("Exiting...");
    System.exit(1);
  }

  private static void distributeInputs(int total) {
    int remaining = total;
    BufferedReader in = null;
    try {
      in = new BufferedReader(new FileReader(input));
    } catch (IOException e) {
      exit(e);
    }
    for (int i = 0; i < nWorkers; i++) {
      PrintWriter w = null;
      try {
        w = new PrintWriter(i + ".in");
      } catch (Exception e) { exit(e); }

      for (int j = 0; j < total / nWorkers; j++) {
        try {
          w.println(in.readLine());
        } catch (IOException e) {System.err.println(e.getMessage());}
      }
      String line = null;
      if (i == nWorkers - 1) {
        while (true) {
          try {
            line = in.readLine();
          } catch (IOException e) {System.err.println(e.getMessage());}
          if (line != null) w.println(line);
        }
      }
      w.close();
    }
    try {
      in.close();
    } catch (IOException e) {}
  }

  private static Integer countLines(String filename) {
    Process p = null;
    try {
      p = Runtime.getRuntime().exec("wc -l " + input);
      p.waitFor();
      BufferedReader reader = new BufferedReader(
                                new InputStreamReader(p.getInputStream()));
      String line = reader.readLine();
      return Integer.parseInt(line.split("\\s+")[0]);
    } catch (IOException e) {
      exit(e);
    } catch (InterruptedException e) {
      exit(e);
    }
    // why java
    return null;
  }

  private static StanfordCoreNLP initPipelines() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
    return new StanfordCoreNLP(props);
  }

  private static void openInput(String filename) {
    try {
      in = new BufferedReader(new FileReader(filename));
    } catch (FileNotFoundException e) {
      exit(e);
    }
  }

  private static JsonObject read() {
    String line = null;
    try {
      line = in.readLine();
    } catch (IOException e) {
      System.err.println(e.getMessage());
      return null;
    }
    if (line == null) return null;
    JsonReader reader = Json.createReader(new StringReader(line));
    JsonObject object = reader.readObject();
    reader.close();
    return object;
  }

  /*
  String date = object.getJsonString("date").getString();
  String title = object.getJsonString("title").getString();
  String url = object.getJsonString("url").getString();
  String text = object.getJsonString("text").getString();
  int id = object.getInt("articleId");*/

  private static void processAnnotations() {
    /*
    JsonObject obj = null;
    PrintWriter xmlOut = null;
    try {
      xmlOut = new PrintWriter(output);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    ReentrantLock l = new ReentrantLock();
    int count = 0;

    while ( (obj = read()) != null) {
      count++;
      pool.submit(new Handler(xmlOut, pipelines, obj, l));
      if (count % 10000 == 0) System.out.println("Scheduled " + count + " utterances");
    }
    pool.shutdown();
    try {
      boolean done = pool.awaitTermination(1, TimeUnit.DAYS);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.err.println("pool.awaitTermination failed");
    }
    IOUtils.closeIgnoringExceptions(xmlOut); */
  }
}

class Handler implements Runnable {
  JsonObject obj;
  ReentrantLock l;
  PrintWriter xmlOut;
  BlockingQueue<StanfordCoreNLP> pipelines;

  Handler(PrintWriter xmlOut, BlockingQueue<StanfordCoreNLP> pipelines, 
      JsonObject obj, ReentrantLock l) { 
    this.pipelines = pipelines;
    this.obj = obj; 
    this.l = l; 
    this.xmlOut = xmlOut; 
  }

  public void run() { 
    Annotation annotation = null;
    StanfordCoreNLP pipeline = null;
    try {
      pipeline = pipelines.take();
    } catch (InterruptedException e) {
      System.err.println(e.getMessage());
      return;
    }
    try {
      annotation = new Annotation(obj.getJsonString("text").getString());
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.err.println(obj);
      return;
    }
    pipeline.annotate(annotation);
    pipelines.add(pipeline);
    l.lock();
    try {
      xmlOut.println(obj.getInt("articleId"));
      pipeline.xmlPrint(annotation, xmlOut);  
      xmlOut.println();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    l.unlock();
  }
}
