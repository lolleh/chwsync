package org.openmrs.chwsync.android.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
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
public final class OutboxDao_Impl implements OutboxDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OutboxEntity> __insertionAdapterOfOutboxEntity;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<OutboxEntity> __updateAdapterOfOutboxEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  public OutboxDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOutboxEntity = new EntityInsertionAdapter<OutboxEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `outbox` (`id`,`clientChangeId`,`entityType`,`uuid`,`operation`,`baseVersion`,`payload`,`createdAt`,`attempts`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OutboxEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getClientChangeId());
        statement.bindString(3, entity.getEntityType());
        statement.bindString(4, entity.getUuid());
        statement.bindString(5, entity.getOperation());
        if (entity.getBaseVersion() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getBaseVersion());
        }
        final String _tmp = __converters.fromJsonObject(entity.getPayload());
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, _tmp);
        }
        statement.bindLong(8, entity.getCreatedAt());
        statement.bindLong(9, entity.getAttempts());
      }
    };
    this.__updateAdapterOfOutboxEntity = new EntityDeletionOrUpdateAdapter<OutboxEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `outbox` SET `id` = ?,`clientChangeId` = ?,`entityType` = ?,`uuid` = ?,`operation` = ?,`baseVersion` = ?,`payload` = ?,`createdAt` = ?,`attempts` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OutboxEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getClientChangeId());
        statement.bindString(3, entity.getEntityType());
        statement.bindString(4, entity.getUuid());
        statement.bindString(5, entity.getOperation());
        if (entity.getBaseVersion() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getBaseVersion());
        }
        final String _tmp = __converters.fromJsonObject(entity.getPayload());
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, _tmp);
        }
        statement.bindLong(8, entity.getCreatedAt());
        statement.bindLong(9, entity.getAttempts());
        statement.bindLong(10, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM outbox WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM outbox";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final OutboxEntity item, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfOutboxEntity.insertAndReturnId(item);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final OutboxEntity item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfOutboxEntity.handle(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clear(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClear.acquire();
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
          __preparedStmtOfClear.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object pending(final int limit,
      final Continuation<? super List<OutboxEntity>> $completion) {
    final String _sql = "SELECT * FROM outbox ORDER BY createdAt ASC, id ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OutboxEntity>>() {
      @Override
      @NonNull
      public List<OutboxEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfClientChangeId = CursorUtil.getColumnIndexOrThrow(_cursor, "clientChangeId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfOperation = CursorUtil.getColumnIndexOrThrow(_cursor, "operation");
          final int _cursorIndexOfBaseVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "baseVersion");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final List<OutboxEntity> _result = new ArrayList<OutboxEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OutboxEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpClientChangeId;
            _tmpClientChangeId = _cursor.getString(_cursorIndexOfClientChangeId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpOperation;
            _tmpOperation = _cursor.getString(_cursorIndexOfOperation);
            final Long _tmpBaseVersion;
            if (_cursor.isNull(_cursorIndexOfBaseVersion)) {
              _tmpBaseVersion = null;
            } else {
              _tmpBaseVersion = _cursor.getLong(_cursorIndexOfBaseVersion);
            }
            final JsonObject _tmpPayload;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfPayload)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfPayload);
            }
            _tmpPayload = __converters.toJsonObject(_tmp);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            _item = new OutboxEntity(_tmpId,_tmpClientChangeId,_tmpEntityType,_tmpUuid,_tmpOperation,_tmpBaseVersion,_tmpPayload,_tmpCreatedAt,_tmpAttempts);
            _result.add(_item);
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
  public Object forRecord(final String uuid,
      final Continuation<? super List<OutboxEntity>> $completion) {
    final String _sql = "SELECT * FROM outbox WHERE uuid = ? ORDER BY createdAt ASC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, uuid);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OutboxEntity>>() {
      @Override
      @NonNull
      public List<OutboxEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfClientChangeId = CursorUtil.getColumnIndexOrThrow(_cursor, "clientChangeId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfOperation = CursorUtil.getColumnIndexOrThrow(_cursor, "operation");
          final int _cursorIndexOfBaseVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "baseVersion");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final List<OutboxEntity> _result = new ArrayList<OutboxEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OutboxEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpClientChangeId;
            _tmpClientChangeId = _cursor.getString(_cursorIndexOfClientChangeId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpOperation;
            _tmpOperation = _cursor.getString(_cursorIndexOfOperation);
            final Long _tmpBaseVersion;
            if (_cursor.isNull(_cursorIndexOfBaseVersion)) {
              _tmpBaseVersion = null;
            } else {
              _tmpBaseVersion = _cursor.getLong(_cursorIndexOfBaseVersion);
            }
            final JsonObject _tmpPayload;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfPayload)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfPayload);
            }
            _tmpPayload = __converters.toJsonObject(_tmp);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            _item = new OutboxEntity(_tmpId,_tmpClientChangeId,_tmpEntityType,_tmpUuid,_tmpOperation,_tmpBaseVersion,_tmpPayload,_tmpCreatedAt,_tmpAttempts);
            _result.add(_item);
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
  public Object latestForRecord(final String uuid,
      final Continuation<? super OutboxEntity> $completion) {
    final String _sql = "SELECT * FROM outbox WHERE uuid = ? ORDER BY createdAt DESC, id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, uuid);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<OutboxEntity>() {
      @Override
      @Nullable
      public OutboxEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfClientChangeId = CursorUtil.getColumnIndexOrThrow(_cursor, "clientChangeId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfOperation = CursorUtil.getColumnIndexOrThrow(_cursor, "operation");
          final int _cursorIndexOfBaseVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "baseVersion");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final OutboxEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpClientChangeId;
            _tmpClientChangeId = _cursor.getString(_cursorIndexOfClientChangeId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpOperation;
            _tmpOperation = _cursor.getString(_cursorIndexOfOperation);
            final Long _tmpBaseVersion;
            if (_cursor.isNull(_cursorIndexOfBaseVersion)) {
              _tmpBaseVersion = null;
            } else {
              _tmpBaseVersion = _cursor.getLong(_cursorIndexOfBaseVersion);
            }
            final JsonObject _tmpPayload;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfPayload)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfPayload);
            }
            _tmpPayload = __converters.toJsonObject(_tmp);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            _result = new OutboxEntity(_tmpId,_tmpClientChangeId,_tmpEntityType,_tmpUuid,_tmpOperation,_tmpBaseVersion,_tmpPayload,_tmpCreatedAt,_tmpAttempts);
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
  public Flow<Integer> observeCount() {
    final String _sql = "SELECT COUNT(*) FROM outbox";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"outbox"}, new Callable<Integer>() {
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

  @Override
  public Flow<List<OutboxEntity>> observeAll() {
    final String _sql = "SELECT * FROM outbox ORDER BY createdAt ASC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"outbox"}, new Callable<List<OutboxEntity>>() {
      @Override
      @NonNull
      public List<OutboxEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfClientChangeId = CursorUtil.getColumnIndexOrThrow(_cursor, "clientChangeId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfOperation = CursorUtil.getColumnIndexOrThrow(_cursor, "operation");
          final int _cursorIndexOfBaseVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "baseVersion");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final List<OutboxEntity> _result = new ArrayList<OutboxEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OutboxEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpClientChangeId;
            _tmpClientChangeId = _cursor.getString(_cursorIndexOfClientChangeId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpOperation;
            _tmpOperation = _cursor.getString(_cursorIndexOfOperation);
            final Long _tmpBaseVersion;
            if (_cursor.isNull(_cursorIndexOfBaseVersion)) {
              _tmpBaseVersion = null;
            } else {
              _tmpBaseVersion = _cursor.getLong(_cursorIndexOfBaseVersion);
            }
            final JsonObject _tmpPayload;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfPayload)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfPayload);
            }
            _tmpPayload = __converters.toJsonObject(_tmp);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            _item = new OutboxEntity(_tmpId,_tmpClientChangeId,_tmpEntityType,_tmpUuid,_tmpOperation,_tmpBaseVersion,_tmpPayload,_tmpCreatedAt,_tmpAttempts);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
