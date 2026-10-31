package com.spendtrack.app

import com.spendtrack.app.data.local.PersonDao
import com.spendtrack.app.data.local.PersonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InMemoryPersonDao : PersonDao {
    private val people = mutableListOf<PersonEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<PersonEntity>> = flowOf(people.sortedBy { it.name.lowercase() })
    override fun observeActive(): Flow<List<PersonEntity>> = flowOf(people.filter { it.isActive }.sortedBy { it.name.lowercase() })
    override suspend fun getByNormalizedName(normalizedName: String) = people.firstOrNull { it.normalizedName == normalizedName }
    override suspend fun getById(id: Long) = people.firstOrNull { it.id == id }
    override suspend fun insert(person: PersonEntity): Long {
        val id = if (person.id == 0L) nextId++ else person.id
        people += person.copy(id = id)
        return id
    }
    override suspend fun update(person: PersonEntity) {
        val index = people.indexOfFirst { it.id == person.id }
        if (index >= 0) people[index] = person
    }
    override suspend fun delete(person: PersonEntity) { people.removeAll { it.id == person.id } }
    override suspend fun clear() { people.clear(); nextId = 1L }
}
