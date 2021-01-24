package example.model

data class Recipe(
    val title: String,
    val description: String,
    val ingredients: List<String>,
    val directions: List<String>,
    val prepTimeMin: Int,
    val cookTimeMin: Int,
    val servings: Int,
    val tags: List<String>,
    val author: Author,
    // we will use this as our ID as well
    val sourceUrl: String
)
