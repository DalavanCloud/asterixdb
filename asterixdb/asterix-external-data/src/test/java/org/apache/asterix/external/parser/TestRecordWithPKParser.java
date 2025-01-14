/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.external.parser;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.asterix.external.api.IRawRecord;
import org.apache.asterix.external.api.IRecordDataParser;
import org.apache.asterix.external.api.IRecordWithPKDataParser;
import org.apache.asterix.external.input.record.RecordWithPK;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;

public class TestRecordWithPKParser<T> implements IRecordWithPKDataParser<RecordWithPK<T>> {

    private final IRecordDataParser<T> recordParser;

    public TestRecordWithPKParser(final IRecordDataParser<T> recordParser) {
        this.recordParser = recordParser;
    }

    @Override
    public void parse(final IRawRecord<? extends RecordWithPK<T>> record, final DataOutput out)
            throws HyracksDataException {
        recordParser.parse(record.get().getRecord(), out);
    }

    @Override
    public void appendKeys(final ArrayTupleBuilder tb, final IRawRecord<? extends RecordWithPK<T>> record)
            throws HyracksDataException {
        record.get().appendPrimaryKeyToTuple(tb);
    }

}
