package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.calculator.MathEvaluator
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.calculator.CalculatorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext_matchesAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Scientific Calculator", appName)
    }

    @Test
    fun mathEvaluator_basicMath_isCorrect() {
        // Test basic addition, subtraction, division, products
        assertEquals(14.0, MathEvaluator.evaluate("2 + 3 * 4"), 1e-9)
        assertEquals(5.5, MathEvaluator.evaluate("11 / 2"), 1e-9)
        assertEquals(2.0, MathEvaluator.evaluate("10 % 4"), 1e-9)
        assertEquals(-2.0, MathEvaluator.evaluate("-2"), 1e-9)
    }

    @Test
    fun mathEvaluator_advancedMath_isCorrect() {
        // Test square roots and trigonometry in Degrees
        assertEquals(4.0, MathEvaluator.evaluate("sqrt(16)"), 1e-9)
        assertEquals(1.0, MathEvaluator.evaluate("sin(90)", isDegree = true), 1e-9)
        assertEquals(0.5, MathEvaluator.evaluate("cos(60)", isDegree = true), 1e-9)
        assertEquals(1.0, MathEvaluator.evaluate("tan(45)", isDegree = true), 1e-9)
        
        // Exponentiation
        assertEquals(8.0, MathEvaluator.evaluate("2 ^ 3"), 1e-9)
        
        // Constants (pi and e)
        assertEquals(Math.PI, MathEvaluator.evaluate("π"), 1e-9)
        assertEquals(Math.E, MathEvaluator.evaluate("e"), 1e-9)
    }

    @Test
    fun roomDatabase_savingCalculations_isPersistent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // In-memory Room database setup
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = HistoryRepository(db.historyDao())

        val historyEntity = HistoryEntity(
            expression = "sin(30) + 10",
            result = "10.5",
            type = "scientific"
        )
        
        repo.insert(historyEntity)
        val list = repo.allHistory.first()
        
        assertEquals(1, list.size)
        assertEquals("sin(30) + 10", list[0].expression)
        assertEquals("10.5", list[0].result)
        
        db.close()
    }
}
