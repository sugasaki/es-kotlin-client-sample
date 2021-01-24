package `dsls`

import com.jillesvangurp.eskotlinwrapper.dsl.TermQuery

/**
 * Extending and Customizing the Kotlin DSLs
 */
fun main() {
    val termQuery = TermQuery("myField", "someValue") {
        boost = 10.0
    }
    println(termQuery.toString())

    //
    val termQuery2 = TermQuery("myField", "someValue") {
        // we support boost
        boost = 2.0
        // but foo is not something we support
        // but we can still add it to the TermQueryConfig
        // because it is backed by MapBackedProperties
        // and implements Map<String, Any>
        this["foo"] = "bar"
    }
    println(termQuery2)
}
