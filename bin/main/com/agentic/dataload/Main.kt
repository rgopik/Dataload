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
data class Question(val qno: Int, val board:String, val cls:String, val chapter:String, val question: String, val options: List<String>, val correctAnswerIndex: Int)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: run with <serviceAccount.json> [questions.json|Del|null] [collectionName or null] [documentId or null]")
        return
    }
    val serviceAccountPath = args[0]
    val arg2 = if (args.size > 1 && args[1] != "null" && args[1].isNotEmpty()) args[1] else null
    val collectionName = if (args.size > 2 && args[2] != "null" && args[2].isNotEmpty()) args[2] else null
    val documentId = if (args.size > 3 && args[3] != "null" && args[3].isNotEmpty()) args[3] else null
    val chapter = if (arg2 == null && args.size > 3 && args[3] != "null" && args[3].isNotEmpty()) args[3] else null
    val modValue = if (args.size > 4 && args[4] != "null" && args[4].isNotEmpty()) args[4] else null
    require(File(serviceAccountPath).exists()) { "Service account file not found: $serviceAccountPath" }

    if (arg2 == null) {
        // Validate Firestore collection, possibly for a specific chapter
        if (collectionName == null) {
            println("Either questions file or collection name must be provided.")
            return
        }
        FirestoreValidator.initFirebase(serviceAccountPath)
        FirestoreValidator.validateQuestionsCollection(collectionName, chapter)
        return
    }


    if (arg2 == "Del" && collectionName != null && documentId != null) {
        // Delete document from Firestore
        FirestoreValidator.initFirebase(serviceAccountPath)
        val db: Firestore = FirestoreClient.getFirestore()
        val docRef = db.collection(collectionName).document(documentId)
        val result = docRef.delete().get()
        println("Deleted document $documentId from collection $collectionName.")
        return
    }

    if (arg2 == "Mod" && collectionName != null && documentId != null && modValue != null) {
        // Update 'count' field in Firestore document
        FirestoreValidator.initFirebase(serviceAccountPath)
        val db: Firestore = FirestoreClient.getFirestore()
        val docRef = db.collection(collectionName).document(documentId)
        docRef.update("count", modValue.toIntOrNull() ?: modValue).get()
        println("Updated document $documentId in collection $collectionName: set count = $modValue")
        return
    }

    if (arg2 == "F5" && collectionName != null) {
        // Auto resequence qno in Firestore collection, chapter is mandatory
        if (args.size < 4 || args[3] == "null" || args[3].isEmpty()) {
            println("Error: Chapter argument is required for resequencing. Usage: <serviceAccount.json> F5 <collectionName> <chapterName>")
            return
        }
        val chapterArg = args[3]
        FirestoreValidator.initFirebase(serviceAccountPath)
        val db: Firestore = FirestoreClient.getFirestore()
        val docs = db.collection(collectionName).get().get().documents
        val filteredDocs = docs.filter { doc ->
            val chapter = doc.getString("chapter")
            chapter != null && chapter.equals(chapterArg, ignoreCase = true)
        }
        // Sort by qno (if present), else by document id
        val docsWithQno = filteredDocs.mapNotNull { doc ->
            val qno = doc.getLong("qno")?.toInt()
            if (qno != null) Pair(doc, qno) else null
        }.sortedBy { it.second }
        val target = "chapter '$chapterArg'"
        println("Resequencing qno for ${docsWithQno.size} documents in $target...")
        for ((idx, pair) in docsWithQno.withIndex()) {
            val (doc, _) = pair
            val newQno = idx + 1
            doc.reference.update("qno", newQno).get()
            println("Updated document ${doc.id} to qno $newQno")
        }
        println("All qno resequenced for $target.")
        return
    }

    val questionsJsonPath = arg2
    require(File(questionsJsonPath).exists()) { "Questions JSON file not found: $questionsJsonPath" }

    val options = FirebaseOptions.builder()
        .setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(FileInputStream(serviceAccountPath)))
        .build()
    FirebaseApp.initializeApp(options)
    val db: Firestore = FirestoreClient.getFirestore()

    val jsonString = Files.readString(Paths.get(questionsJsonPath))
    val jsonElement = Json.parseToJsonElement(jsonString)
    require(jsonElement is JsonObject && jsonElement.entries.size == 1) { "JSON must have a single top-level key (the collection name)" }
    val (fileCollectionName, questionsElement) = jsonElement.entries.first()
    val questions: List<Question> = Json.decodeFromJsonElement(
        kotlinx.serialization.builtins.ListSerializer(Question.serializer()), questionsElement
    )
    val uploadCollection = collectionName ?: fileCollectionName
    println("Uploading ${questions.size} questions to collection '$uploadCollection'...")
    for ((i, q) in questions.withIndex()) {
        val doc = hashMapOf(
            "qno" to q.qno,
            "board" to q.board,
            "cls" to q.cls,
            "chapter" to q.chapter,
            "question" to q.question,
            "options" to q.options,
            "correctAnswerIndex" to q.correctAnswerIndex
        )
        val ref = db.collection(uploadCollection).document()
        ref.set(doc).get() // Wait for completion
        println("Uploaded question ${i + 1}: ${q.question}")
    }

    // Create metadata document with count
    val chapterName = questions.firstOrNull()?.chapter
    val metaDocName = if (chapterName != null) {
        (fileCollectionName + "_" + chapterName).replace(" ", "")
    } else {
        fileCollectionName.replace(" ", "")
    }
    val metaData = mapOf("count" to questions.size)
    db.collection("metadata").document(metaDocName).set(metaData).get()
    println("Metadata document '$metaDocName' created/updated with count: ${questions.size}")

    println("All questions uploaded.")
}
