package org.openmrs.chwsync.android.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;
import kotlinx.serialization.json.JsonObject;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RecordDao_Impl implements RecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RecordEntity> __insertionAdapterOfRecordEntity;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public RecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRecordEntity = new EntityInsertionAdapter<RecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `records` (`type`,`uuid`,`version`,`data`,`deleted`,`dirty`,`changedAt`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RecordEntity entity) {
        statement.bindString(1, entity.getType());
        statement.bindString(2, entity.getUuid());
        if (entity.getVersion() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getVersion());
        }
        final String _tmp = __converters.fromJsonObject(entity.getData());
        if (_tmp == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, _tmp);
        }
        final int _tmp_1 = entity.getDeleted() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        final int _tmp_2 = entity.getDirty() ? 1 : 0;
        statement.bindLong(6, _tmp_2);
        statement.bindString(7, entity.getChangedAt());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM records WHERE type = ? AND uuid = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final RecordEntity record, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfRecordEntity.insert(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String type, final String uuid,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, type);
        _argIndex = 2;
        _stmt.bindString(_argIndex, uuid);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<RecordEntity>> observeByType(final String type) {
    final String _sql = "SELECT * FROM records WHERE type = ? AND deleted = 0 ORDER BY changedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, type);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"records"}, new Callable<List<RecordEntity>>() {
      @Override
      @NonNull
      public List<RecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfData = CursorUtil.getColumnIndexOrThrow(_cursor, "data");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final int _cursorIndexOfChangedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "changedAt");
          final List<RecordEntity> _result = new ArrayList<RecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RecordEntity _item;
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Long _tmpVersion;
            if (_cursor.isNull(_cursorIndexOfVersion)) {
              _tmpVersion = null;
            } else {
              _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            }
            final JsonObject _tmpData;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfData)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfData);
            }
            final JsonObject _tmp_1 = __converters.toJsonObject(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'kotlinx.serialization.json.JsonObject', but it was NULL.");
            } else {
              _tmpData = _tmp_1;
            }
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            final String _tmpChangedAt;
            _tmpChangedAt = _cursor.getString(_cursorIndexOfChangedAt);
            _item = new RecordEntity(_tmpType,_tmpUuid,_tmpVersion,_tmpData,_tmpDeleted,_tmpDirty,_tmpChangedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<RecordEntity> observe(final String type, final String uuid) {
    final String _sql = "SELECT * FROM records WHERE type = ? AND uuid = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, type);
    _argIndex = 2;
    _statement.bindString(_argIndex, uuid);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"records"}, new Callable<RecordEntity>() {
      @Override
      @Nullable
      public RecordEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfData = CursorUtil.getColumnIndexOrThrow(_cursor, "data");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final int _cursorIndexOfChangedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "changedAt");
          final RecordEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Long _tmpVersion;
            if (_cursor.isNull(_cursorIndexOfVersion)) {
              _tmpVersion = null;
            } else {
              _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            }
            final JsonObject _tmpData;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfData)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfData);
            }
            final JsonObject _tmp_1 = __converters.toJsonObject(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'kotlinx.serialization.json.JsonObject', but it was NULL.");
            } else {
              _tmpData = _tmp_1;
            }
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            final String _tmpChangedAt;
            _tmpChangedAt = _cursor.getString(_cursorIndexOfChangedAt);
            _result = new RecordEntity(_tmpType,_tmpUuid,_tmpVersion,_tmpData,_tmpDeleted,_tmpDirty,_tmpChangedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object get(final String type, final String uuid,
      final Continuation<? super RecordEntity> $completion) {
    final String _sql = "SELECT * FROM records WHERE type = ? AND uuid = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, type);
    _argIndex = 2;
    _statement.bindString(_argIndex, uuid);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RecordEntity>() {
      @Override
      @Nullable
      public RecordEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfData = CursorUtil.getColumnIndexOrThrow(_cursor, "data");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final int _cursorIndexOfChangedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "changedAt");
          final RecordEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Long _tmpVersion;
            if (_cursor.isNull(_cursorIndexOfVersion)) {
              _tmpVersion = null;
            } else {
              _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            }
            final JsonObject _tmpData;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfData)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfData);
            }
            final JsonObject _tmp_1 = __converters.toJsonObject(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'kotlinx.serialization.json.JsonObject', but it was NULL.");
            } else {
              _tmpData = _tmp_1;
            }
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            final String _tmpChangedAt;
            _tmpChangedAt = _cursor.getString(_cursorIndexOfChangedAt);
            _result = new RecordEntity(_tmpType,_tmpUuid,_tmpVersion,_tmpData,_tmpDeleted,_tmpDirty,_tmpChangedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> countByType(final String type) {
    final String _sql = "SELECT COUNT(*) FROM records WHERE type = ? AND deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, type);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"records"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
