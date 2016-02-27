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
package org.apache.asterix.metadata.declared;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.asterix.common.exceptions.AsterixException;
import org.apache.asterix.metadata.entities.Dataset;
import org.apache.asterix.metadata.entities.InternalDatasetDetails;
import org.apache.asterix.metadata.utils.DatasetUtils;
import org.apache.asterix.om.types.ARecordType;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.IAType;
import org.apache.commons.lang.StringUtils;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.api.exceptions.HyracksDataException;

public class LoadableDataSource extends AqlDataSource {

    private final Dataset targetDataset;
    private final List<List<String>> partitioningKeys;
    private final String adapter;
    private final Map<String, String> adapterProperties;
    private final boolean isPKAutoGenerated;

    public LoadableDataSource(Dataset targetDataset, IAType itemType, IAType metaItemType, String adapter,
            Map<String, String> properties) throws AlgebricksException, IOException {
        super(new AqlSourceId("loadable_dv", "loadable_ds"), itemType, metaItemType, AqlDataSourceType.LOADABLE);
        this.targetDataset = targetDataset;
        this.adapter = adapter;
        this.adapterProperties = properties;
        partitioningKeys = DatasetUtils.getPartitioningKeys(targetDataset);
        ARecordType recType = (ARecordType) itemType;
        isPKAutoGenerated = ((InternalDatasetDetails) targetDataset.getDatasetDetails()).isAutogenerated();
        if (isPKAutoGenerated) {
            // Since the key is auto-generated, we need to use another
            // record type (possibly nested) which has all fields except the PK
            try {
                recType = getStrippedPKType(new LinkedList<String>(partitioningKeys.get(0)), recType);
            } catch (AsterixException e) {
                throw new AlgebricksException(e);
            }
        }
        schemaTypes = new IAType[] { recType };
    }

    private ARecordType getStrippedPKType(List<String> partitioningKeys, ARecordType recType)
            throws AsterixException, HyracksDataException {
        List<String> fieldNames = new LinkedList<>();
        List<IAType> fieldTypes = new LinkedList<>();
        int j = 0;
        for (int i = 0; i < recType.getFieldNames().length; i++) {
            IAType fieldType = null;
            if (partitioningKeys.get(0).equals(recType.getFieldNames()[j])) {
                if (recType.getFieldTypes()[j].getTypeTag() == ATypeTag.RECORD) {
                    if (j != 0) {
                        throw new AsterixException("Autogenerated key " + StringUtils.join(partitioningKeys, '.')
                                + " should be a first field of the type " + recType.getTypeName());
                    }
                    partitioningKeys.remove(0);
                    fieldType = getStrippedPKType(partitioningKeys, (ARecordType) recType.getFieldTypes()[j]);
                } else {
                    j++;
                    continue;
                }
            } else {
                fieldType = recType.getFieldTypes()[j];
            }
            fieldTypes.add(fieldType);
            fieldNames.add(recType.getFieldNames()[j]);
            j++;
        }
        return new ARecordType(recType.getTypeName(), fieldNames.toArray(new String[0]),
                fieldTypes.toArray(new IAType[0]), recType.isOpen());
    }

    public List<List<String>> getPartitioningKeys() {
        return partitioningKeys;
    }

    public String getAdapter() {
        return adapter;
    }

    public Map<String, String> getAdapterProperties() {
        return adapterProperties;
    }

    public IAType getLoadedType() {
        return schemaTypes[schemaTypes.length - 1];
    }

    public Dataset getTargetDataset() {
        return targetDataset;
    }
}
