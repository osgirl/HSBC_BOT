package ml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.mllib.classification.LogisticRegressionModel;
import org.apache.spark.mllib.classification.LogisticRegressionWithLBFGS;
import org.apache.spark.mllib.feature.HashingTF;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.regression.LabeledPoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by louis on 12/01/17.
 */
public class HSBCSparkInstance {
    private final static String APP_NAME = "HSBC";
    private final static String MASTER_URL= "local";
    // Create a HashingTF instance to map question text to vectors of 100 features.
    private final HashingTF tf = new HashingTF(100);

    private List<Intent> intents ;

    private JavaRDD<LabeledPoint> trainingData;
    private LogisticRegressionModel model;
    private JavaSparkContext sparkContext;

    private HashMap<String, Response> responseManager = new HashMap<String, Response>();

    /**Create HSBCSparkInstance object from database retrieved through the API**/
    @JsonCreator
    public HSBCSparkInstance(@JsonProperty("intents") List<Intent> intents){
        this.intents = intents;

        SparkConf sparkConf = new SparkConf().setAppName(APP_NAME);
        sparkConf.setMaster(MASTER_URL);
        System.out.println("Launch app: "+APP_NAME+" on "+MASTER_URL);
        sparkContext = new JavaSparkContext(sparkConf);

        transformIntentsToPoints();

    }

   public void transformIntentsToPoints(){
       for(Intent intent : intents){
           intent.transformToPoints(tf, sparkContext);
       }
   }

    /** Use data from intents to learn and generate a model **/
    public void learnAndCreateModel(){
        System.out.println("Starting creation of the model...");
        System.out.println("Adding intent's data...");

        for( Intent intent : intents){
            /**For the first iteration trainingData is null**/
            trainingData = trainingData == null ?  intent.getRdd() :  trainingData.union(intent.getRdd());
            System.out.println("One RDD added");
        }
        trainingData.cache();
        LogisticRegressionWithLBFGS lrLBFGS = new LogisticRegressionWithLBFGS();
        lrLBFGS.setNumClasses(intents.size());
        model = lrLBFGS.run(trainingData.rdd());
        System.out.println("Model created");
    }

    private Double predict(String question){
        Vector questionAsVector = tf.transform(Arrays.asList(question.split(" ")));
        return model.predict(questionAsVector);
    }

    public String findResponse(String question, String username) throws IOException {
        int label = predict(question).intValue();
        for(Intent intent : intents){
            if(intent.getLabel() == label){
                String response = intent.getResponse();
                if(intent.getIsFeedbackIntentNo() || intent.getIsFeedbackIntentYes()){
                    Response previousResponse = responseManager.get(username);
                    if(previousResponse == null)
                        return "C'est gentil de me répondre, mais je n'ai posé aucune question";
                    else{
                        previousResponse.setCorrect(intent.getIsFeedbackIntentYes());
                        previousResponse.save();
                    }
                }
                else{
                    if(responseManager.containsKey(username))
                        responseManager.remove(username);
                    responseManager.put(username, new Response(question, response));
                }

                return intent.getResponse();
            }
        }
        return "Je n'ai aucune réponse à vous fournir.";
    }

    public HashMap<String, Response> getResponseManager() {
        return responseManager;
    }

    /**Getters**/

    public LogisticRegressionModel getModel(){
        return model;
    }

    public HashingTF getTf(){
        return tf;
    }

    public List<Intent> getIntents() {
        return intents;
    }

}
