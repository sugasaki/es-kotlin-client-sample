package `dsls`

import com.jillesvangurp.eskotlinwrapper.mapProps

/**
 * Extending and Customizing the Kotlin DSLs
 */
fun main() {

    val aCustomObject = mapProps {
        // mixed type lists
        this["icanhasjson"] = listOf(1, 2, "4")
        this["meaning_of_life"] = 42
        this["nested_object"] = mapProps {
            this["another"] = mapProps {
                this["nested_object_prop"] = 42
            }
            this["some more stuff"] = "you get the point"
        }
    }

    println(aCustomObject)
}
