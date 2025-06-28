package com.agentic.dataload

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import java.io.FileInputStream
import java.io.File
import com.google.cloud.firestore.Firestore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Paths

@Serializable
data class AnatomyQuestion(val question: String, val options: List<String>, val correctAnswerIndex: Int)

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: run with <serviceAccount.json> <questions.json>")
        return
    }
    val serviceAccountPath = args[0]
    val questionsJsonPath = args[1]
    require(File(serviceAccountPath).exists()) { "Service account file not found: $serviceAccountPath" }
    require(File(questionsJsonPath).exists()) { "Questions JSON file not found: $questionsJsonPath" }

    val options = FirebaseOptions.builder()
        .setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(FileInputStream(serviceAccountPath)))
        .build()
    FirebaseApp.initializeApp(options)
    val db: Firestore = FirestoreClient.getFirestore()

    val jsonString = Files.readString(Paths.get(questionsJsonPath))
    val jsonElement = Json.parseToJsonElement(jsonString)
    require(jsonElement is JsonObject && jsonElement.entries.size == 1) { "JSON must have a single top-level key (the collection name)" }
    val (collectionName, questionsElement) = jsonElement.entries.first()
    val questions: List<AnatomyQuestion> = Json.decodeFromJsonElement(
        kotlinx.serialization.builtins.ListSerializer(AnatomyQuestion.serializer()), questionsElement
    )
    println("Uploading ${questions.size} questions to collection '$collectionName'...")
    for ((i, q) in questions.withIndex()) {
        val doc = hashMapOf(
            "question" to q.question,
            "options" to q.options,
            "correctAnswerIndex" to q.correctAnswerIndex
        )
        val ref = db.collection(collectionName).document()
        ref.set(doc).get() // Wait for completion
        println("Uploaded question ${i + 1}: ${q.question}")
    }
    println("All questions uploaded.")
}
