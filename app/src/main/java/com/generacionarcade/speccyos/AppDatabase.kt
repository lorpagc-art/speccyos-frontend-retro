package com.generacionarcade.speccyos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities  = [Game::class, AiCache::class],
    version   = 7,
    // exportSchema = true genera app/schemas/*.json: sin ellos es IMPOSIBLE
    // escribir un test de migración y las migraciones se validan a ciegas.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun aiCacheDao(): AiCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * MIGRACIONES.
         *
         * EL FALLO QUE ARREGLA ESTE BLOQUE
         * --------------------------------
         * Room no valida solo las COLUMNAS al abrir la base de datos: valida el
         * esquema entero, ÍNDICES INCLUIDOS. La entidad `Game` declara siete:
         *
         *   index_games_platformId, _isFavorite, _lastPlayed, _playCount,
         *   _playTimeSeconds, _raGameId y el compuesto _platformId_title
         *
         * y ninguna de las migraciones creaba NI UNO. Con `ALTER TABLE ADD COLUMN`
         * se añade la columna, pero el índice hay que crearlo aparte.
         *
         * Consecuencia real: en una instalación LIMPIA, Room ejecuta createAllTables
         * y crea los siete, así que todo funciona. En una ACTUALIZACIÓN sobre una
         * versión anterior, la tabla se queda sin ellos y al abrirla salta
         *
         *   IllegalStateException: Migration didn't properly handle: games(...Game)
         *
         * es decir: la app arranca, aparece la ventana en blanco y se cierra en
         * cuanto el ViewModel toca la base de datos. Solo le pasa a quien ya tenía
         * SpeccyOS instalado, que son justamente todos los usuarios reales.
         *
         * Cada migración crea ahora los índices de las columnas que añade, y la
         * 6→7 vuelve a asegurarlos TODOS con IF NOT EXISTS: así queda correcta
         * cualquier base de datos, venga por el camino que venga.
         */

        // Sentencias copiadas literalmente de app/schemas/…/7.json, que es lo que
        // Room compara al abrir. No reescribir a mano: si el texto no coincide con
        // el esquema exportado, la validación vuelve a fallar.
        private val GAMES_INDICES = arrayOf(
            "CREATE INDEX IF NOT EXISTS `index_games_platformId` ON `games` (`platformId`)",
            "CREATE INDEX IF NOT EXISTS `index_games_isFavorite` ON `games` (`isFavorite`)",
            "CREATE INDEX IF NOT EXISTS `index_games_lastPlayed` ON `games` (`lastPlayed`)",
            "CREATE INDEX IF NOT EXISTS `index_games_playCount` ON `games` (`playCount`)",
            "CREATE INDEX IF NOT EXISTS `index_games_playTimeSeconds` ON `games` (`playTimeSeconds`)",
            "CREATE INDEX IF NOT EXISTS `index_games_raGameId` ON `games` (`raGameId`)",
            "CREATE INDEX IF NOT EXISTS `index_games_platformId_title` ON `games` (`platformId`, `title`)"
        )

        private val GAMES_INDEX_NAMES = arrayOf(
            "index_games_platformId", "index_games_isFavorite", "index_games_lastPlayed",
            "index_games_playCount", "index_games_playTimeSeconds", "index_games_raGameId",
            "index_games_platformId_title"
        )

        private const val CREATE_AI_CACHE =
            "CREATE TABLE IF NOT EXISTS `ai_cache` (`queryHash` TEXT NOT NULL, " +
            "`responseText` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`queryHash`))"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN lastPlayed INTEGER NOT NULL DEFAULT 0")
                // Las tres columnas van indexadas en la entidad: sin estas tres
                // líneas la actualización deja la tabla sin índices y Room la
                // rechaza al abrirla.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_isFavorite` ON `games` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_playCount` ON `games` (`playCount`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastPlayed` ON `games` (`lastPlayed`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN videoPreview TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN wheel TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN fanart TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN cdArt TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN screenshot TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN md5 TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN crc32 TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN sha1 TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN raGameId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN raAchievementsTotal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN raAchievementsEarned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN raLastSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_raGameId` ON `games` (`raGameId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN playTimeSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_playTimeSeconds` ON `games` (`playTimeSeconds`)")
            }
        }

        /**
         * v7: se elimina el índice sobre `boxArt` y se RECONCILIA el resto.
         *
         * Los índices forman parte del hash de identidad del esquema, así que
         * quitar uno exige subir de versión. Y como ninguna migración anterior
         * llegó a crear los suyos en los dispositivos ya instalados, aquí se
         * aseguran los siete con IF NOT EXISTS: es idempotente y deja la base de
         * datos idéntica a la de una instalación limpia, venga de la versión que
         * venga. También se asegura `ai_cache`, por si la tabla se añadió sin su
         * propia migración.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_games_boxArt")
                db.execSQL(CREATE_AI_CACHE)
                // Se borra y se vuelve a crear cada índice en vez de confiar solo en
                // IF NOT EXISTS: si en una versión antigua existía un índice con el
                // mismo nombre pero sobre otras columnas, IF NOT EXISTS lo daría por
                // bueno y la validación seguiría fallando. Reconstruirlos cuesta
                // milisegundos en una biblioteca de unos miles de juegos.
                GAMES_INDEX_NAMES.forEach { db.execSQL("DROP INDEX IF EXISTS `$it`") }
                GAMES_INDICES.forEach { db.execSQL(it) }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "speccy_os_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                    )
                    // NO volver a poner aqui fallbackToDestructiveMigrationFrom(1, 2, 3, 4).
                    //
                    // Room PROHIBE que una version aparezca a la vez en una migracion y
                    // en la lista de esa llamada, y lo comprueba dentro de build(), es
                    // decir al construir la base de datos, no al abrirla:
                    //
                    //   IllegalArgumentException: Inconsistency detected. A Migration was
                    //   supplied to addMigration() that has a start or end version equal
                    //   to a start version supplied to fallbackToDestructiveMigrationFrom()
                    //
                    // Como getDatabase() se llama desde el constructor de MainViewModel, la
                    // excepcion salia envuelta en "Cannot create an instance of class
                    // MainViewModel" durante la primera composicion: la app pintaba la
                    // ventana en blanco y se cerraba, en TODOS los dispositivos y con la
                    // base de datos vacia o llena. Como las migraciones 1->7 ya estan
                    // completas, esa llamada no aportaba nada.
                    //
                    // Ultimo recurso ante cualquier desajuste de esquema que las
                    // migraciones no cubran. Sin esto, un solo indice que falte deja la app
                    // sin abrir y al usuario sin mas salida que borrar los datos a mano. Se
                    // pierden favoritos y tiempo jugado (la biblioteca se reescanea sola),
                    // que es mucho menos malo que una app que no arranca.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.query("PRAGMA synchronous = NORMAL").close()
                            db.query("PRAGMA temp_store = MEMORY").close()
                            db.query("PRAGMA cache_size = -8000").close()
                            db.query("PRAGMA mmap_size = 268435456").close()
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
