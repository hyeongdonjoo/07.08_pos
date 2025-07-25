package com.example.myapplication2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PaymentGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_guide)

        val shopName = intent.getStringExtra("shopName") ?: ""
        val totalPrice = intent.getIntExtra("totalPrice", 0)
        val menuSummary = intent.getStringExtra("menuSummary") ?: ""

        findViewById<Button>(R.id.buttonStartPayment).setOnClickListener {
            sendOrderToFirebase(shopName, totalPrice, menuSummary)
        }
    }

    private fun sendOrderToFirebase(shopName: String, totalPrice: Int, menuSummary: String) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = menuSummary.split(",").map {
            val parts = it.trim().split(" x ")
            mapOf(
                "name" to (parts.getOrNull(0) ?: ""),
                "quantity" to (parts.getOrNull(1)?.toIntOrNull() ?: 1)
            )
        }

        // 매장별 카운터 문서 지정 (orders_counter_매장명)
        val counterRef = db.collection("system").document("orders_counter_$shopName")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val currentOrderNumber = snapshot.getLong("currentOrderNumber") ?: 0L
            val nextOrderNumber = currentOrderNumber + 1

            // 카운터 값 업데이트
            transaction.update(counterRef, "currentOrderNumber", nextOrderNumber)

            // 주문 데이터 생성
            val orderData = hashMapOf(
                "orderNumber" to nextOrderNumber,
                "shopName" to shopName,
                "items" to items,
                "totalPrice" to totalPrice,
                "timestamp" to Timestamp.now()
            )

            // 주문 저장 (orders 컬렉션에)
            val ordersRef = db.collection("users").document(userId).collection("orders")
            transaction.set(ordersRef.document(), orderData)

            // 트랜잭션 결과 반환
            nextOrderNumber
        }.addOnSuccessListener { orderNumber ->
            // 주문완료 화면으로 이동 (orderNumber 전달)
            val intent = Intent(this, OrderCompleteActivity::class.java).apply {
                putExtra("shopName", shopName)
                putExtra("totalPrice", totalPrice)
                putExtra("menuSummary", menuSummary)
                putExtra("orderNumber", orderNumber.toString())
            }
            startActivity(intent)
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "주문 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
