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

package org.apache.asterix.runtime.evaluators.functions.records;

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import org.apache.asterix.builders.RecordBuilder;
import org.apache.asterix.om.pointables.ARecordVisitablePointable;
import org.apache.asterix.om.pointables.base.DefaultOpenFieldType;
import org.apache.asterix.om.pointables.base.IVisitablePointable;
import org.apache.asterix.om.pointables.cast.ACastVisitor;
import org.apache.asterix.om.types.ARecordType;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.IAType;
import org.apache.asterix.runtime.evaluators.functions.PointableHelper;
import org.apache.hyracks.algebricks.common.utils.Triple;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluator;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.UTF8StringPointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

class RecordRemoveEvaluator implements IScalarEvaluator {

    private final IPointable inputRecordPointable = new VoidPointable();
    private final UTF8StringPointable fieldToRemovePointable = new UTF8StringPointable();
    private final IBinaryComparator stringBinaryComparator = PointableHelper.createStringBinaryComparator();
    private final ArrayBackedValueStorage resultStorage = new ArrayBackedValueStorage();
    private final DataOutput resultOutput = resultStorage.getDataOutput();
    private final RecordBuilder outRecordBuilder = new RecordBuilder();
    private final IScalarEvaluator eval0;
    private final IScalarEvaluator eval1;
    private final ARecordVisitablePointable openRecordPointable;
    private ARecordVisitablePointable inputRecordVisitable;
    private boolean requiresCast = false;
    private ACastVisitor castVisitor;
    private Triple<IVisitablePointable, IAType, Boolean> castVisitorArg;

    RecordRemoveEvaluator(IScalarEvaluator eval0, IScalarEvaluator eval1, ARecordType recordType) {
        this.eval0 = eval0;
        this.eval1 = eval1;
        openRecordPointable = new ARecordVisitablePointable(DefaultOpenFieldType.NESTED_OPEN_RECORD_TYPE);
        if (recordType != null) {
            inputRecordVisitable = new ARecordVisitablePointable(recordType);
            if (hasDerivedType(recordType.getFieldTypes())) {
                requiresCast = true;
                castVisitor = new ACastVisitor();
                castVisitorArg =
                        new Triple<>(openRecordPointable, openRecordPointable.getInputRecordType(), Boolean.FALSE);
            }
        }
    }

    @Override
    public void evaluate(IFrameTupleReference tuple, IPointable result) throws HyracksDataException {
        resultStorage.reset();
        boolean returnNull = false;
        eval0.evaluate(tuple, inputRecordPointable);
        byte[] data = inputRecordPointable.getByteArray();
        int offset = inputRecordPointable.getStartOffset();
        byte typeTag = data[offset];
        if (typeTag != ATypeTag.SERIALIZED_RECORD_TYPE_TAG) {
            returnNull = true;
        }
        eval1.evaluate(tuple, fieldToRemovePointable);
        data = fieldToRemovePointable.getByteArray();
        offset = fieldToRemovePointable.getStartOffset();
        typeTag = data[offset];
        if (typeTag != ATypeTag.SERIALIZED_STRING_TYPE_TAG) {
            returnNull = true;
        }
        if (returnNull) {
            PointableHelper.setNull(result);
            return;
        }
        evaluate();
        result.set(resultStorage);
    }

    private void evaluate() throws HyracksDataException {
        resultStorage.reset();
        try {
            final ARecordVisitablePointable inputRecord = getInputRecordVisitablePointable();
            buildOutputRecord(inputRecord);
        } catch (IOException e) {
            throw HyracksDataException.create(e);
        }
    }

    private void buildOutputRecord(ARecordVisitablePointable inputRecord) throws HyracksDataException {
        outRecordBuilder.reset(DefaultOpenFieldType.NESTED_OPEN_RECORD_TYPE);
        outRecordBuilder.init();
        final List<IVisitablePointable> fieldNames = inputRecord.getFieldNames();
        final List<IVisitablePointable> fieldValues = inputRecord.getFieldValues();
        for (int i = 0, fieldCount = fieldNames.size(); i < fieldCount; i++) {
            final IVisitablePointable fieldName = fieldNames.get(i);
            if (!PointableHelper.isEqual(fieldName, fieldToRemovePointable, stringBinaryComparator)) {
                outRecordBuilder.addField(fieldName, fieldValues.get(i));
            }
        }
        outRecordBuilder.write(resultOutput, true);
    }

    private ARecordVisitablePointable getInputRecordVisitablePointable() throws HyracksDataException {
        inputRecordVisitable.set(inputRecordPointable);
        if (requiresCast) {
            return castToOpenRecord();
        }
        return inputRecordVisitable;
    }

    private boolean hasDerivedType(IAType[] types) {
        for (IAType type : types) {
            if (type.getTypeTag().isDerivedType()) {
                return true;
            }
        }
        return false;
    }

    private ARecordVisitablePointable castToOpenRecord() throws HyracksDataException {
        inputRecordVisitable.accept(castVisitor, castVisitorArg);
        return openRecordPointable;
    }
}
