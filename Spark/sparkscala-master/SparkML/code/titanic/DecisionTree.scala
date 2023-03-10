//https://www.kaggle.com/c/titanic
package com.tekcrux.kaggle.titanic

import org.apache.spark.sql.SparkSession
import org.apache.spark.ml._
import org.apache.spark.ml.feature._
import org.apache.spark.ml.classification.RandomForestClassifier

import org.apache.spark.ml.classification.DecisionTreeClassificationModel
import org.apache.spark.ml.classification.DecisionTreeClassifier

import org.apache.spark.sql.functions._
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.ml.tuning.ParamGridBuilder
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.tuning.CrossValidator
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.mllib.evaluation.MulticlassMetrics

import com.tekcrux.kaggle.MLUtils.accuracyScore

object DecisionTree {
  def main(args: Array[String]) {

    System.setProperty("hadoop.home.dir", "C:\\hadoop\\");
    
    val sparkSession = SparkSession
                       .builder
                       .master("local[4]")
                       .appName("kaggle_titanic")
                       .getOrCreate()

    sparkSession.sparkContext.setLogLevel("ERROR")

    val trainDataFile = "data/titanic/train.csv"
    val testDataFile = "data/titanic/test.csv"
          
    //load train df
    val df = sparkSession.read.option("header", "true").option("inferSchema", "true").csv(trainDataFile)
    df.printSchema()

    //handle missing values 
    val meanValue = df.agg(mean(df("Age"))).first.getDouble(0)
    val fixedDf = df.na.fill(meanValue, Array("Age"))
       
    //test and train split
    val dfs = fixedDf.randomSplit(Array(0.7, 0.3))
    val trainDf = dfs(0).withColumnRenamed("Survived", "label")
    val crossDf = dfs(1)

    // create pipeline stages for handling categorical
    val genderStages = handleCategorical("Sex")            // return two transformers - SI & OHE
    val embarkedStages = handleCategorical("Embarked")
    val pClassStages = handleCategorical("Pclass")

    //columns for training
    val cols = Array("Sex_onehot", "Embarked_onehot", "Pclass_onehot", "SibSp", "Parch", "Age", "Fare")
    
    val vectorAssembler = new VectorAssembler()
                            .setInputCols(cols)
                            .setOutputCol("features")

    //algorithm stage
   // Train a DecisionTree model.
   val decisionTreeClassifier = new DecisionTreeClassifier()
                                .setLabelCol("label")
                                .setFeaturesCol("features")
    
    //pipeline
    val preProcessStages = genderStages ++ embarkedStages ++ pClassStages ++ Array(vectorAssembler)
    val pipeline = new Pipeline().setStages(preProcessStages ++ Array(decisionTreeClassifier))

    val model = pipeline.fit(trainDf)
   
    println("Train accuracy with Pipeline: " + accuracyScore(model.transform(trainDf), "label", "prediction"))
    println("Test accuracy with Pipeline: " + accuracyScore(model.transform(crossDf), "Survived", "prediction"))

    model.transform(crossDf).printSchema()
    
    val testDf = sparkSession.read.option("header", "true").option("inferSchema", "true").csv(testDataFile)
    val fareMeanValue = df.agg(mean(df("Fare"))).first.getDouble(0)
    val fixedOutputDf = testDf.na.fill(meanValue, Array("age")).na.fill(fareMeanValue, Array("Fare"))

    generateOutputFile(fixedOutputDf, model)
    
    println("Output file has been generated successfully. Done!!! ")    
  }
  
  def generateOutputFile(testDF: DataFrame, model: Model[_]) = {
    val outputDir = "data/titanic/output/decisiontree/"
    val scoredDf = model.transform(testDF)
    
    val outputDf = scoredDf.select("PassengerId", "prediction")
    val castedDf = outputDf.select(outputDf("PassengerId"), outputDf("prediction").cast(IntegerType).as("Survived"))
    castedDf.write.format("csv").option("header", "true").mode(SaveMode.Overwrite).save(outputDir)
  }

  def crossValidation(pipeline: Pipeline, paramMap: Array[ParamMap], df: DataFrame): Model[_] = {
    val cv = new CrossValidator()
      .setEstimator(pipeline)
      .setEvaluator(new BinaryClassificationEvaluator)
      .setEstimatorParamMaps(paramMap)
      .setNumFolds(5)
    cv.fit(df)
  }

  def handleCategorical(column: String): Array[PipelineStage] = {   
    

    val stringIndexer = new StringIndexer()
      .setInputCol(column)
      .setOutputCol(s"${column}_index")
      .setHandleInvalid("skip")
      
    val oneHot = new OneHotEncoder()
                     .setInputCol(s"${column}_index")
                     .setOutputCol(s"${column}_onehot")
                     
    Array(stringIndexer, oneHot)
  }
}
