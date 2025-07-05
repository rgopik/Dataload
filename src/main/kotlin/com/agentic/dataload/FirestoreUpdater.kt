package com.agentic.dataload

import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient

object FirestoreUpdater {
    fun updateField(collection: String, documentId: String, field: String, value: Any) {
        val db: Firestore = FirestoreClient.getFirestore()
        val docRef = db.collection(collection).document(documentId)
        val updateResult = docRef.update(field, value)
        updateResult.get() // Wait for update to complete
        println("Updated $field in document $documentId of collection $collection to $value")
    }

    fun updateFields(collection: String, documentId: String, updates: Map<String, Any>) {
        val db: Firestore = FirestoreClient.getFirestore()
        val docRef = db.collection(collection).document(documentId)
        val updateResult = docRef.update(updates)
        updateResult.get() // Wait for update to complete
        println("Updated fields in document $documentId of collection $collection: $updates")
    }

    fun setDocument(collection: String, documentId: String, data: Map<String, Any>) {
        val db: Firestore = FirestoreClient.getFirestore()
        val docRef = db.collection(collection).document(documentId)
        val setResult = docRef.set(data)
        setResult.get() // Wait for set to complete
        println("Set document $documentId in collection $collection with data: $data")
    }

    fun deleteDocument(collection: String, documentId: String) {
        val db: Firestore = FirestoreClient.getFirestore()
        val docRef = db.collection(collection).document(documentId)
        val deleteResult = docRef.delete()
        deleteResult.get() // Wait for delete to complete
        println("Deleted document $documentId from collection $collection")
    }
}
