package org.openmrs.chwsync.android.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ChwSyncDatabase_Impl extends ChwSyncDatabase {
  private volatile RecordDao _recordDao;

  private volatile OutboxDao _outboxDao;

  private volatile ConflictDao _conflictDao;

  private volatile SettingDao _settingDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `records` (`type` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER, `data` TEXT NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `changedAt` TEXT NOT NULL, PRIMARY KEY(`type`, `uuid`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientChangeId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `uuid` TEXT NOT NULL, `operation` TEXT NOT NULL, `baseVersion` INTEGER, `payload` TEXT, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_uuid` ON `outbox` (`uuid`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_clientChangeId` ON `outbox` (`clientChangeId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `conflicts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `uuid` TEXT NOT NULL, `operation` TEXT NOT NULL, `resolution` TEXT NOT NULL, `message` TEXT, `localData` TEXT, `serverData` TEXT, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0dd7accb706a9fc359fe56fb05bc0784')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `records`");
        db.execSQL("DROP TABLE IF EXISTS `outbox`");
        db.execSQL("DROP TABLE IF EXISTS `conflicts`");
        db.execSQL("DROP TABLE IF EXISTS `settings`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsRecords = new HashMap<String, TableInfo.Column>(7);
        _columnsRecords.put("type", new TableInfo.Column("type", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecords.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecords.put("version", new TableInfo.Column("version", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecords.put("data", new TableInfo.Column("data", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecords.put("deleted", new TableInfo.Column("deleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecords.put("dirty", new TableInfo.Column("dirty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecords.put("changedAt", new TableInfo.Column("changedAt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRecords = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRecords = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRecords = new TableInfo("records", _columnsRecords, _foreignKeysRecords, _indicesRecords);
        final TableInfo _existingRecords = TableInfo.read(db, "records");
        if (!_infoRecords.equals(_existingRecords)) {
          return new RoomOpenHelper.ValidationResult(false, "records(org.openmrs.chwsync.android.data.local.RecordEntity).\n"
                  + " Expected:\n" + _infoRecords + "\n"
                  + " Found:\n" + _existingRecords);
        }
        final HashMap<String, TableInfo.Column> _columnsOutbox = new HashMap<String, TableInfo.Column>(9);
        _columnsOutbox.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("clientChangeId", new TableInfo.Column("clientChangeId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("entityType", new TableInfo.Column("entityType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("operation", new TableInfo.Column("operation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("baseVersion", new TableInfo.Column("baseVersion", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("payload", new TableInfo.Column("payload", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutbox.put("attempts", new TableInfo.Column("attempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOutbox = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesOutbox = new HashSet<TableInfo.Index>(2);
        _indicesOutbox.add(new TableInfo.Index("index_outbox_uuid", false, Arrays.asList("uuid"), Arrays.asList("ASC")));
        _indicesOutbox.add(new TableInfo.Index("index_outbox_clientChangeId", true, Arrays.asList("clientChangeId"), Arrays.asList("ASC")));
        final TableInfo _infoOutbox = new TableInfo("outbox", _columnsOutbox, _foreignKeysOutbox, _indicesOutbox);
        final TableInfo _existingOutbox = TableInfo.read(db, "outbox");
        if (!_infoOutbox.equals(_existingOutbox)) {
          return new RoomOpenHelper.ValidationResult(false, "outbox(org.openmrs.chwsync.android.data.local.OutboxEntity).\n"
                  + " Expected:\n" + _infoOutbox + "\n"
                  + " Found:\n" + _existingOutbox);
        }
        final HashMap<String, TableInfo.Column> _columnsConflicts = new HashMap<String, TableInfo.Column>(9);
        _columnsConflicts.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("entityType", new TableInfo.Column("entityType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("operation", new TableInfo.Column("operation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("resolution", new TableInfo.Column("resolution", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("message", new TableInfo.Column("message", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("localData", new TableInfo.Column("localData", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("serverData", new TableInfo.Column("serverData", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConflicts.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysConflicts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesConflicts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoConflicts = new TableInfo("conflicts", _columnsConflicts, _foreignKeysConflicts, _indicesConflicts);
        final TableInfo _existingConflicts = TableInfo.read(db, "conflicts");
        if (!_infoConflicts.equals(_existingConflicts)) {
          return new RoomOpenHelper.ValidationResult(false, "conflicts(org.openmrs.chwsync.android.data.local.ConflictEntity).\n"
                  + " Expected:\n" + _infoConflicts + "\n"
                  + " Found:\n" + _existingConflicts);
        }
        final HashMap<String, TableInfo.Column> _columnsSettings = new HashMap<String, TableInfo.Column>(2);
        _columnsSettings.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("value", new TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSettings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSettings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSettings = new TableInfo("settings", _columnsSettings, _foreignKeysSettings, _indicesSettings);
        final TableInfo _existingSettings = TableInfo.read(db, "settings");
        if (!_infoSettings.equals(_existingSettings)) {
          return new RoomOpenHelper.ValidationResult(false, "settings(org.openmrs.chwsync.android.data.local.SettingEntity).\n"
                  + " Expected:\n" + _infoSettings + "\n"
                  + " Found:\n" + _existingSettings);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "0dd7accb706a9fc359fe56fb05bc0784", "4a61bdaa1dacd7578fefb09090c3d6c7");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "records","outbox","conflicts","settings");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `records`");
      _db.execSQL("DELETE FROM `outbox`");
      _db.execSQL("DELETE FROM `conflicts`");
      _db.execSQL("DELETE FROM `settings`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(RecordDao.class, RecordDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(OutboxDao.class, OutboxDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConflictDao.class, ConflictDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SettingDao.class, SettingDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public RecordDao records() {
    if (_recordDao != null) {
      return _recordDao;
    } else {
      synchronized(this) {
        if(_recordDao == null) {
          _recordDao = new RecordDao_Impl(this);
        }
        return _recordDao;
      }
    }
  }

  @Override
  public OutboxDao outbox() {
    if (_outboxDao != null) {
      return _outboxDao;
    } else {
      synchronized(this) {
        if(_outboxDao == null) {
          _outboxDao = new OutboxDao_Impl(this);
        }
        return _outboxDao;
      }
    }
  }

  @Override
  public ConflictDao conflicts() {
    if (_conflictDao != null) {
      return _conflictDao;
    } else {
      synchronized(this) {
        if(_conflictDao == null) {
          _conflictDao = new ConflictDao_Impl(this);
        }
        return _conflictDao;
      }
    }
  }

  @Override
  public SettingDao settings() {
    if (_settingDao != null) {
      return _settingDao;
    } else {
      synchronized(this) {
        if(_settingDao == null) {
          _settingDao = new SettingDao_Impl(this);
        }
        return _settingDao;
      }
    }
  }
}
