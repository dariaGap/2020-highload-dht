package ru.mail.polis.dao.dariagap;

import org.jetbrains.annotations.NotNull;
import org.rocksdb.RocksIterator;
import ru.mail.polis.Record;
import ru.mail.polis.util.Util;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class RecordIterator implements Iterator<Record>, AutoCloseable {
    private final RocksIterator iterator;

    public RecordIterator(@NotNull final RocksIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return iterator.isValid();
    }

    @Override
    public Record next() {
        if(!hasNext()) {
            throw new NoSuchElementException("Next on empty iterator");
        }

        final byte[] key = iterator.key();
        final byte[] value = iterator.value();
        iterator.next();
        return Record.of(Util.unpack(key),ByteBuffer.wrap(value));
    }

    @Override
    public void close() {
        iterator.close();
    }
}
