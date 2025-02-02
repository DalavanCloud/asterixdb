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
package org.apache.asterix.om.typecomputer.impl;

import org.apache.asterix.om.exceptions.UnsupportedTypeException;
import org.apache.asterix.om.typecomputer.base.AbstractResultTypeComputer;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.AUnionType;
import org.apache.asterix.om.types.IAType;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import org.apache.hyracks.api.exceptions.SourceLocation;

public class MinMaxAggTypeComputer extends AbstractResultTypeComputer {

    public static final MinMaxAggTypeComputer INSTANCE = new MinMaxAggTypeComputer();

    private MinMaxAggTypeComputer() {
    }

    @Override
    protected void checkArgType(String funcName, int argIndex, IAType type, SourceLocation sourceLoc)
            throws AlgebricksException {
        ATypeTag tag = type.getTypeTag();
        switch (tag) {
            case DOUBLE:
            case FLOAT:
            case BIGINT:
            case INTEGER:
            case SMALLINT:
            case TINYINT:
            case STRING:
            case DATE:
            case TIME:
            case DATETIME:
            case YEARMONTHDURATION:
            case DAYTIMEDURATION:
            case ANY:
                return;
            default:
                throw new UnsupportedTypeException(sourceLoc, funcName, tag);
        }
    }

    @Override
    protected IAType getResultType(ILogicalExpression expr, IAType... strippedInputTypes) throws AlgebricksException {
        return AUnionType.createUnknownableType(strippedInputTypes[0]);
    }
}
