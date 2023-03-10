package scala.examples

import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}
import org.apache.spark.sql.SparkSession

object TFIDF {
  def main(args: Array[String]) {

    val spark = SparkSession
      .builder
      .appName("TFIDF")
      .master("local[2]")
      .getOrCreate()

    // $example on$
    val sentenceData = spark.createDataFrame(Seq(
      (0.0, "Hi I heard about Spark"),
      (0.0, "I wish Java could use case classes"),
      (1.0, "Logistic regression models are neat")
    )).toDF("label", "sentence")
    
    sentenceData.show()

    val tokenizer = new Tokenizer()
                        .setInputCol("sentence")
                        .setOutputCol("words")
                        
    val wordsData = tokenizer.transform(sentenceData)

    wordsData.show()
    
    val hashingTF = new HashingTF()
                        .setInputCol("words")
                        .setOutputCol("rawFeatures")
                        .setNumFeatures(20)

    val featurizedData = hashingTF.transform(wordsData)
    
    featurizedData.show()
    
    // alternatively, CountVectorizer can also be used to get term frequency vectors
    val idf = new IDF()
                  .setInputCol("rawFeatures")
                  .setOutputCol("features")                  
    val idfModel = idf.fit(featurizedData)

    val rescaledData = idfModel.transform(featurizedData)
    rescaledData.select("label", "features").show()
    // $example off$

    spark.stop()
  }
}