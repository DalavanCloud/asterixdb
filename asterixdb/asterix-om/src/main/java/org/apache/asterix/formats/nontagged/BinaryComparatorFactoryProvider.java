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
package org.apache.asterix.formats.nontagged;

import java.io.Serializable;

import org.apache.asterix.dataflow.data.nontagged.comparators.ACirclePartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.ADurationPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.AIntervalAscPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.AIntervalDescPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.ALinePartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.AGenericAscBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.AGenericDescBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.APoint3DPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.APointPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.APolygonPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.ARectanglePartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.AUUIDPartialBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.BooleanBinaryComparatorFactory;
import org.apache.asterix.dataflow.data.nontagged.comparators.RawBinaryComparatorFactory;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.IAType;
import org.apache.hyracks.algebricks.data.IBinaryComparatorFactoryProvider;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import org.apache.hyracks.data.std.primitive.ByteArrayPointable;
import org.apache.hyracks.data.std.primitive.BytePointable;
import org.apache.hyracks.data.std.primitive.DoublePointable;
import org.apache.hyracks.data.std.primitive.FloatPointable;
import org.apache.hyracks.data.std.primitive.IntegerPointable;
import org.apache.hyracks.data.std.primitive.LongPointable;
import org.apache.hyracks.data.std.primitive.ShortPointable;
import org.apache.hyracks.data.std.primitive.UTF8StringLowercasePointable;
import org.apache.hyracks.data.std.primitive.UTF8StringLowercaseTokenPointable;
import org.apache.hyracks.data.std.primitive.UTF8StringPointable;

public class BinaryComparatorFactoryProvider implements IBinaryComparatorFactoryProvider, Serializable {

    private static final long serialVersionUID = 1L;
    public static final BinaryComparatorFactoryProvider INSTANCE = new BinaryComparatorFactoryProvider();
    public static final PointableBinaryComparatorFactory BYTE_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(BytePointable.FACTORY);
    public static final PointableBinaryComparatorFactory SHORT_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(ShortPointable.FACTORY);
    public static final PointableBinaryComparatorFactory INTEGER_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(IntegerPointable.FACTORY);
    public static final PointableBinaryComparatorFactory LONG_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(LongPointable.FACTORY);
    public static final PointableBinaryComparatorFactory FLOAT_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(FloatPointable.FACTORY);
    public static final PointableBinaryComparatorFactory DOUBLE_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(DoublePointable.FACTORY);
    public static final PointableBinaryComparatorFactory UTF8STRING_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(UTF8StringPointable.FACTORY);
    // Equivalent to UTF8STRING_POINTABLE_INSTANCE but all characters are considered lower case to implement
    // case-insensitive comparisons.
    public static final PointableBinaryComparatorFactory UTF8STRING_LOWERCASE_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(UTF8StringLowercasePointable.FACTORY);
    // Equivalent to UTF8STRING_LOWERCASE_POINTABLE_INSTANCE but the length information is kept separately,
    // rather than keeping them in the beginning of a string. It is especially useful for the string tokens
    public static final PointableBinaryComparatorFactory UTF8STRING_LOWERCASE_TOKEN_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(UTF8StringLowercaseTokenPointable.FACTORY);
    public static final PointableBinaryComparatorFactory BINARY_POINTABLE_INSTANCE =
            new PointableBinaryComparatorFactory(ByteArrayPointable.FACTORY);

    private BinaryComparatorFactoryProvider() {
    }

    // This method add the option of ignoring the case in string comparisons.
    // TODO: We should incorporate this option more nicely, but I'd have to change algebricks.
    @Override
    public IBinaryComparatorFactory getBinaryComparatorFactory(Object type, boolean ascending, boolean ignoreCase) {
        return getBinaryComparatorFactory(type, type, ascending, ignoreCase);
    }

    @Override
    public IBinaryComparatorFactory getBinaryComparatorFactory(Object type, boolean ascending) {
        return getBinaryComparatorFactory(type, type, ascending);
    }

    @Override
    public IBinaryComparatorFactory getBinaryComparatorFactory(Object leftType, Object rightType, boolean ascending,
            boolean ignoreCase) {
        if (leftType == null || rightType == null) {
            return createGenericBinaryComparatorFactory(null, null, ascending);
        }
        IAType left = (IAType) leftType;
        IAType right = (IAType) rightType;
        if (left.getTypeTag() == ATypeTag.STRING && right.getTypeTag() == ATypeTag.STRING && ignoreCase) {
            return addOffset(UTF8STRING_LOWERCASE_POINTABLE_INSTANCE, ascending);
        }
        return createGenericBinaryComparatorFactory(left, right, ascending);
    }

    @Override
    public IBinaryComparatorFactory getBinaryComparatorFactory(Object leftType, Object rightType, boolean ascending) {
        // During a comparison, since proper type promotion among several numeric types are required,
        // we will use AGenericAscBinaryComparatorFactory, instead of using a specific comparator
        return createGenericBinaryComparatorFactory((IAType) leftType, (IAType) rightType, ascending);
    }

    public IBinaryComparatorFactory getBinaryComparatorFactory(ATypeTag type, boolean ascending) {
        switch (type) {
            case ANY:
            case UNION:
                // we could do smth better for nullable fields
                return createGenericBinaryComparatorFactory(null, null, ascending);
            case NULL:
            case MISSING:
                return new AnyBinaryComparatorFactory();
            case BOOLEAN:
                return addOffset(BooleanBinaryComparatorFactory.INSTANCE, ascending);
            case TINYINT:
                return addOffset(BYTE_POINTABLE_INSTANCE, ascending);
            case SMALLINT:
                return addOffset(SHORT_POINTABLE_INSTANCE, ascending);
            case DATE:
            case TIME:
            case YEARMONTHDURATION:
            case INTEGER:
                return addOffset(INTEGER_POINTABLE_INSTANCE, ascending);
            case DATETIME:
            case DAYTIMEDURATION:
            case BIGINT:
                return addOffset(LONG_POINTABLE_INSTANCE, ascending);
            case FLOAT:
                return addOffset(FLOAT_POINTABLE_INSTANCE, ascending);
            case DOUBLE:
                return addOffset(DOUBLE_POINTABLE_INSTANCE, ascending);
            case STRING:
                return addOffset(UTF8STRING_POINTABLE_INSTANCE, ascending);
            case RECTANGLE:
                return addOffset(ARectanglePartialBinaryComparatorFactory.INSTANCE, ascending);
            case CIRCLE:
                return addOffset(ACirclePartialBinaryComparatorFactory.INSTANCE, ascending);
            case POINT:
                return addOffset(APointPartialBinaryComparatorFactory.INSTANCE, ascending);
            case POINT3D:
                return addOffset(APoint3DPartialBinaryComparatorFactory.INSTANCE, ascending);
            case LINE:
                return addOffset(ALinePartialBinaryComparatorFactory.INSTANCE, ascending);
            case POLYGON:
                return addOffset(APolygonPartialBinaryComparatorFactory.INSTANCE, ascending);
            case DURATION:
                return addOffset(ADurationPartialBinaryComparatorFactory.INSTANCE, ascending);
            case INTERVAL:
                return addOffset(intervalBinaryComparatorFactory(ascending), ascending);
            case UUID:
                return addOffset(AUUIDPartialBinaryComparatorFactory.INSTANCE, ascending);
            case BINARY:
                return addOffset(BINARY_POINTABLE_INSTANCE, ascending);
            default:
                return addOffset(RawBinaryComparatorFactory.INSTANCE, ascending);
        }
    }

    private IBinaryComparatorFactory addOffset(final IBinaryComparatorFactory inst, final boolean ascending) {
        return new OrderedBinaryComparatorFactory(inst, ascending);
    }

    private IBinaryComparatorFactory createGenericBinaryComparatorFactory(IAType leftType, IAType rightType,
            boolean ascending) {
        if (ascending) {
            return new AGenericAscBinaryComparatorFactory(leftType, rightType);
        } else {
            return new AGenericDescBinaryComparatorFactory(leftType, rightType);
        }
    }

    private IBinaryComparatorFactory intervalBinaryComparatorFactory(boolean ascending) {
        // Intervals have separate binary comparator factories, since asc is primarily based on start point
        // and desc is similarly based on end point.
        if (ascending) {
            return AIntervalAscPartialBinaryComparatorFactory.INSTANCE;
        } else {
            return AIntervalDescPartialBinaryComparatorFactory.INSTANCE;
        }
    }

}
