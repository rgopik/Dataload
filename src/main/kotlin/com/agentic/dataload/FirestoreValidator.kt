package com.agentic.dataload

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient

object FirestoreValidator {
    fun initFirebase(serviceAccountPath: String) {
        val serviceAccount = java.io.FileInputStream(serviceAccountPath)
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        }
    }

    fun validateQuestionsCollection(collectionName: String, chapter: String? = null) {
        val db: Firestore = FirestoreClient.getFirestore()
        val allCollections = db.listCollections().map { it.id }
        var usedCollectionName = collectionName
        var metaCollectionName = "${collectionName}_${chapter}".replace(" ","")
        var docs = db.collection(usedCollectionName).get().get().documents
        // If chapter is provided, filter docs by chapter
        if (chapter != null) {
            docs = docs.filter { it.getString("chapter") == chapter }
        }
        // Check for metadata.<usedCollectionName>.count
        val dbMeta = db.collection("metadata").document(metaCollectionName).get().get()
        val expectedCount = dbMeta.getLong("count")?.toInt()
        // Only count documents that have both qno and question
        val actualCount = docs.count { doc ->
            doc.getLong("qno") != null && doc.getString("question") != null
        }
        var countSummaryMsg: String? = null
        if (expectedCount != null) {
            if (expectedCount == actualCount) {
                countSummaryMsg = "Question count matches metadata: $actualCount"
            } else {
                countSummaryMsg = "WARNING: Question count ($actualCount) does not match metadata.$metaCollectionName.count ($expectedCount)"
            }
        } else {
            countSummaryMsg = "No metadata count found for collection '$metaCollectionName'."
          }

        val qnoSet = mutableSetOf<Int>()
        val questionSet = mutableSetOf<Pair<String, List<String>>>()
        val qnoList = mutableListOf<Int>()
        val questionList = mutableListOf<Pair<String, List<String>>>()
        val qnoCountMap = mutableMapOf<Int, Int>()
        val questionDocMap = mutableMapOf<Pair<String, List<String>>, MutableList<Pair<String, Int>>>() // (question, sorted options) -> list of (docId, qno)
        var hasDuplicates = false

        val missingQnoOrQuestionDocs = mutableListOf<String>()
        val blankQuestionDocs = mutableListOf<String>()
        for (doc in docs) {
            val qno = doc.getLong("qno")?.toInt()
            val question = doc.getString("question")?.trim()
            val options = doc.get("options") as? List<*>?
            val optionsStr = options?.map { it.toString() } ?: emptyList()
            val sortedOptions = optionsStr.sorted()

            if (qno == null || question == null) {
                missingQnoOrQuestionDocs.add(doc.id)
                continue
            }
            if (question.isEmpty()) {
                blankQuestionDocs.add(doc.id)
            }

            // Count qno occurrences
            qnoCountMap[qno] = (qnoCountMap[qno] ?: 0) + 1

            // Track question+sortedOptions to docId/qno
            val questionKey = Pair(question, sortedOptions)
            questionDocMap.getOrPut(questionKey) { mutableListOf() }.add(Pair(doc.id, qno))

            // Check for duplicate qno
            if (!qnoSet.add(qno)) {
                hasDuplicates = true
            }
            qnoList.add(qno)

            // Check for duplicate question+sortedOptions
            if (!questionSet.add(questionKey)) {
                hasDuplicates = true
            }
            questionList.add(questionKey)
        }

        // Check for sequential qno
        val sortedQnos = qnoList.sorted()
        val expectedQnos = (sortedQnos.firstOrNull() ?: 1)..(sortedQnos.lastOrNull() ?: 1)
        if (sortedQnos != expectedQnos.toList()) {
            hasDuplicates = true
        }
        
        println("\n--- Validation Summary ---")

        if (!hasDuplicates) {
            println("Validation passed: No duplicate qno, all qno in sequence, no duplicate questions.")
        }

        // --- Summary reporting ---
        // Count duplicate questions (by text+options)
        val questionTextCounts = questionList.groupingBy { it }.eachCount()
        val duplicateQuestions = questionTextCounts.filter { it.value > 1 }
        val duplicateQnos = qnoCountMap.filter { it.value > 1 }

        if (countSummaryMsg != null) println(countSummaryMsg)
        if (missingQnoOrQuestionDocs.isNotEmpty()) {
            missingQnoOrQuestionDocs.forEach { docId ->
                println("$collectionName:$docId missing qno or question")
            }
        }
        if (blankQuestionDocs.isNotEmpty()) {
            println("Questions with no text:")
            blankQuestionDocs.forEach { docId ->
                println("$collectionName:$docId has blank question text")
            }
        }
        // Find gaps in qno
        val missingQnos = mutableListOf<Int>()
        if (sortedQnos.isNotEmpty()) {
            val minQno = sortedQnos.first()
            val maxQno = sortedQnos.last()
            for (q in minQno..maxQno) {
                if (q !in sortedQnos) missingQnos.add(q)
            }
        }
        // Print summary at the bottom
        // Print summary at the bottom in requested order
        if (missingQnos.isEmpty()) {
            println("Gaps in qno : None (all qno present and sequential)")
        } else {
            println("Gaps in qno: No questions with qno ${missingQnos.joinToString(",")}")
        }

        println("Duplicate questions found: ${duplicateQuestions.size}")
        println("Duplicate qnos found:${duplicateQnos.size}")

        if (duplicateQuestions.isNotEmpty()) {
            println("Duplicate question+options pairs:")
            duplicateQuestions.forEach { (key, count) ->
                val (text, options) = key
                println("'$text' | options: ${options}")
                // List all doc ids and qnos for this question+options
                questionDocMap[key]?.forEach { (docId, qno) ->
                    println("(Document: $docId, qno:$qno)")
                }
            }
        }
    }
}
