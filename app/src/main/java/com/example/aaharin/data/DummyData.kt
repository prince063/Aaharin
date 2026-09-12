package com.example.aaharin.data

/**
 * In-memory overlay for orders placed on this device.
 *
 * The table list endpoint lags behind a freshly saved KOT, so newly occupied
 * tables and their items are held here and merged over the server response.
 */
object DummyData {
    // Local cache for recently placed orders to override old server data
    private val localOrders = mutableMapOf<Int, MutableList<CartItem>>()
    private val localOrderIds = mutableMapOf<Int, String>()
    private val occupiedTableIds = mutableSetOf<Int>()

    fun setTableOccupiedLocal(tableId: Int, newItems: List<CartItem> = emptyList(), orderId: String? = null) {
        occupiedTableIds.add(tableId)
        if (orderId != null) {
            localOrderIds[tableId] = orderId
        }
        if (newItems.isNotEmpty()) {
            val existingItems = localOrders[tableId] ?: mutableListOf()
            
            // Append or update quantities of existing items in local cache
            newItems.forEach { newItem ->
                val index = existingItems.indexOfFirst { 
                    it.menuItem.id == newItem.menuItem.id && it.isHalf == newItem.isHalf 
                }
                if (index != -1) {
                    val current = existingItems[index]
                    existingItems[index] = current.copy(quantity = current.quantity + newItem.quantity)
                } else {
                    existingItems.add(newItem)
                }
            }
            localOrders[tableId] = existingItems
        }
    }

    fun getLocalOrder(tableId: Int): List<CartItem>? = localOrders[tableId]
    
    fun getLocalOrderId(tableId: Int): String? = localOrderIds[tableId]

    fun isTableOccupiedLocal(tableId: Int): Boolean = occupiedTableIds.contains(tableId)
    
    fun clearLocalOccupancy(tableId: Int) {
        occupiedTableIds.remove(tableId)
        localOrders.remove(tableId)
        localOrderIds.remove(tableId)
    }

    fun updateTableStatus(tableId: Int, newStatus: TableStatus) {
        if (newStatus == TableStatus.VACANT) {
            clearLocalOccupancy(tableId)
        }
    }
}
