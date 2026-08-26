package app.encore.french

import android.app.Application
import app.encore.french.data.EncoreDatabase
import app.encore.french.data.EncoreRepository

class EncoreApplication : Application() {
    val repository by lazy { EncoreRepository(EncoreDatabase.get(this)) }
}
