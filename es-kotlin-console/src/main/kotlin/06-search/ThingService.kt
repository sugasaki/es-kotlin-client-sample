//package search
//
//import com.jillesvangurp.eskotlinwrapper.IndexRepository
//import org.elasticsearch.action.support.WriteRequest
//
//class ThingService(
//    private val repo: IndexRepository<Thing>
//) {
//
//    /**
//     * Index削除
//     */
//    fun deleteIndex() {
//        repo.deleteIndex()
//    }
//
//    /**
//     * Bulk Indexing
//     * @param bulkSize Int
//     */
//    fun bulkInset(bulkSize: Int = 100) {
//        repo.bulk(refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE) {
//            index("1", Thing("The quick brown fox"))
//            index("2", Thing("The quick brown emu"))
//            index("3", Thing("The quick brown gnu"))
//            index("4", Thing("Another thing"))
//            5.rangeTo(bulkSize).forEach {
//                index("$it", Thing("Another thing: $it"))
//            }
//        }
//    }
//}
