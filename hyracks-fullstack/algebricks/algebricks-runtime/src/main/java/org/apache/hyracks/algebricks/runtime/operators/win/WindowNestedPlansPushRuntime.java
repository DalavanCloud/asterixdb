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

package org.apache.hyracks.algebricks.runtime.operators.win;

import org.apache.hyracks.algebricks.data.IBinaryIntegerInspector;
import org.apache.hyracks.algebricks.data.IBinaryIntegerInspectorFactory;
import org.apache.hyracks.algebricks.runtime.base.IRunningAggregateEvaluatorFactory;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluator;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.api.comm.IFrame;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.exceptions.SourceLocation;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.DataUtils;
import org.apache.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import org.apache.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import org.apache.hyracks.dataflow.common.data.accessors.PointableTupleReference;
import org.apache.hyracks.storage.common.MultiComparator;

/**
 * Runtime for window operators that performs partition materialization and can evaluate running aggregates
 * as well as regular aggregates (in nested plans) over window frames.
 */
class WindowNestedPlansPushRuntime extends AbstractWindowNestedPlansPushRuntime {

    private static final int PARTITION_POSITION_SLOT = 0;

    private static final int FRAME_POSITION_SLOT = 1;

    private static final int TMP_POSITION_SLOT = 2;

    private static final int PARTITION_READER_SLOT_COUNT = TMP_POSITION_SLOT + 1;

    private final boolean frameValueExists;

    private final IScalarEvaluatorFactory[] frameValueEvalFactories;

    private IScalarEvaluator[] frameValueEvals;

    private PointableTupleReference frameValuePointables;

    private final IBinaryComparatorFactory[] frameValueComparatorFactories;

    private MultiComparator frameValueComparators;

    private final boolean frameStartExists;

    private final IScalarEvaluatorFactory[] frameStartEvalFactories;

    private IScalarEvaluator[] frameStartEvals;

    private PointableTupleReference frameStartPointables;

    private final boolean frameStartIsMonotonic;

    private final boolean frameEndExists;

    private final IScalarEvaluatorFactory[] frameEndEvalFactories;

    private IScalarEvaluator[] frameEndEvals;

    private PointableTupleReference frameEndPointables;

    private final boolean frameExcludeExists;

    private final IScalarEvaluatorFactory[] frameExcludeEvalFactories;

    private IScalarEvaluator[] frameExcludeEvals;

    private final int frameExcludeNegationStartIdx;

    private PointableTupleReference frameExcludePointables;

    private IPointable frameExcludePointable2;

    private final IBinaryComparatorFactory[] frameExcludeComparatorFactories;

    private IBinaryComparator[] frameExcludeComparators;

    private final boolean frameOffsetExists;

    private final IScalarEvaluatorFactory frameOffsetEvalFactory;

    private IScalarEvaluator frameOffsetEval;

    private IPointable frameOffsetPointable;

    private final IBinaryIntegerInspectorFactory binaryIntegerInspectorFactory;

    private final int frameMaxObjects;

    private FrameTupleAccessor tAccess2;

    private FrameTupleReference tRef2;

    private IBinaryIntegerInspector bii;

    private int chunkIdxFrameStartGlobal;

    private int tBeginIdxFrameStartGlobal;

    WindowNestedPlansPushRuntime(int[] partitionColumns, IBinaryComparatorFactory[] partitionComparatorFactories,
            IBinaryComparatorFactory[] orderComparatorFactories, IScalarEvaluatorFactory[] frameValueEvalFactories,
            IBinaryComparatorFactory[] frameValueComparatorFactories, IScalarEvaluatorFactory[] frameStartEvalFactories,
            boolean frameStartIsMonotonic, IScalarEvaluatorFactory[] frameEndEvalFactories,
            IScalarEvaluatorFactory[] frameExcludeEvalFactories, int frameExcludeNegationStartIdx,
            IBinaryComparatorFactory[] frameExcludeComparatorFactories, IScalarEvaluatorFactory frameOffsetEvalFactory,
            IBinaryIntegerInspectorFactory binaryIntegerInspectorFactory, int frameMaxObjects, int[] projectionColumns,
            int[] runningAggOutColumns, IRunningAggregateEvaluatorFactory[] runningAggFactories,
            int nestedAggOutSchemaSize, WindowAggregatorDescriptorFactory nestedAggFactory, IHyracksTaskContext ctx,
            int memSizeInFrames, SourceLocation sourceLoc) {
        super(partitionColumns, partitionComparatorFactories, orderComparatorFactories, projectionColumns,
                runningAggOutColumns, runningAggFactories, nestedAggOutSchemaSize, nestedAggFactory, ctx,
                memSizeInFrames, sourceLoc);
        this.frameValueEvalFactories = frameValueEvalFactories;
        this.frameValueExists = frameValueEvalFactories != null && frameValueEvalFactories.length > 0;
        this.frameStartEvalFactories = frameStartEvalFactories;
        this.frameStartExists = frameStartEvalFactories != null && frameStartEvalFactories.length > 0;
        this.frameStartIsMonotonic = frameStartExists && frameStartIsMonotonic;
        this.frameEndEvalFactories = frameEndEvalFactories;
        this.frameEndExists = frameEndEvalFactories != null && frameEndEvalFactories.length > 0;
        this.frameValueComparatorFactories = frameValueComparatorFactories;
        this.frameExcludeEvalFactories = frameExcludeEvalFactories;
        this.frameExcludeExists = frameExcludeEvalFactories != null && frameExcludeEvalFactories.length > 0;
        this.frameExcludeComparatorFactories = frameExcludeComparatorFactories;
        this.frameExcludeNegationStartIdx = frameExcludeNegationStartIdx;
        this.frameOffsetExists = frameOffsetEvalFactory != null;
        this.frameOffsetEvalFactory = frameOffsetEvalFactory;
        this.binaryIntegerInspectorFactory = binaryIntegerInspectorFactory;
        this.frameMaxObjects = frameMaxObjects;
    }

    @Override
    protected void init() throws HyracksDataException {
        super.init();
        if (frameValueExists) {
            frameValueEvals = createEvaluators(frameValueEvalFactories, ctx);
            frameValueComparators = MultiComparator.create(frameValueComparatorFactories);
            frameValuePointables = createPointables(frameValueEvalFactories.length);
        }
        if (frameStartExists) {
            frameStartEvals = createEvaluators(frameStartEvalFactories, ctx);
            frameStartPointables = createPointables(frameStartEvalFactories.length);
        }
        if (frameEndExists) {
            frameEndEvals = createEvaluators(frameEndEvalFactories, ctx);
            frameEndPointables = createPointables(frameEndEvalFactories.length);
        }
        if (frameExcludeExists) {
            frameExcludeEvals = createEvaluators(frameExcludeEvalFactories, ctx);
            frameExcludeComparators = createBinaryComparators(frameExcludeComparatorFactories);
            frameExcludePointables = createPointables(frameExcludeEvalFactories.length);
            frameExcludePointable2 = VoidPointable.FACTORY.createPointable();
        }
        if (frameOffsetExists) {
            frameOffsetEval = frameOffsetEvalFactory.createScalarEvaluator(ctx);
            frameOffsetPointable = VoidPointable.FACTORY.createPointable();
            bii = binaryIntegerInspectorFactory.createBinaryIntegerInspector(ctx);
        }
        tAccess2 = new FrameTupleAccessor(inputRecordDesc);
        tRef2 = new FrameTupleReference();
    }

    @Override
    protected void beginPartitionImpl() throws HyracksDataException {
        super.beginPartitionImpl();
        chunkIdxFrameStartGlobal = -1;
        tBeginIdxFrameStartGlobal = -1;
    }

    @Override
    protected void producePartitionTuples(int chunkIdx, IFrame chunkFrame) throws HyracksDataException {
        partitionReader.savePosition(PARTITION_POSITION_SLOT);

        int nChunks = getPartitionChunkCount();
        boolean isFirstChunkInPartition = chunkIdx == 0;

        tAccess.reset(chunkFrame.getBuffer());
        int tBeginIdx = getTupleBeginIdx(chunkIdx);
        int tEndIdx = getTupleEndIdx(chunkIdx);

        for (int tIdx = tBeginIdx; tIdx <= tEndIdx; tIdx++) {
            boolean isFirstTupleInPartition = isFirstChunkInPartition && tIdx == tBeginIdx;

            tRef.reset(tAccess, tIdx);

            // running aggregates
            produceTuple(tupleBuilder, tAccess, tIdx, tRef);

            // frame boundaries
            if (frameStartExists) {
                evaluate(frameStartEvals, tRef, frameStartPointables);
            }
            if (frameEndExists) {
                evaluate(frameEndEvals, tRef, frameEndPointables);
            }
            if (frameExcludeExists) {
                evaluate(frameExcludeEvals, tRef, frameExcludePointables);
            }
            int toSkip = 0;
            if (frameOffsetExists) {
                frameOffsetEval.evaluate(tRef, frameOffsetPointable);
                toSkip = bii.getIntegerValue(frameOffsetPointable.getByteArray(), frameOffsetPointable.getStartOffset(),
                        frameOffsetPointable.getLength());
            }
            int toWrite = frameMaxObjects;

            nestedAggInit();

            boolean frameStartForward = frameStartIsMonotonic && chunkIdxFrameStartGlobal >= 0;
            int chunkIdxInnerStart = frameStartForward ? chunkIdxFrameStartGlobal : 0;
            int tBeginIdxInnerStart = frameStartForward ? tBeginIdxFrameStartGlobal : -1;

            if (chunkIdxInnerStart < nChunks) {
                if (frameStartForward && !isFirstTupleInPartition) {
                    partitionReader.restorePosition(FRAME_POSITION_SLOT);
                } else {
                    partitionReader.rewind();
                }
            }

            int chunkIdxFrameStartLocal = -1, tBeginIdxFrameStartLocal = -1;

            frame_loop: for (int chunkIdxInner = chunkIdxInnerStart; chunkIdxInner < nChunks; chunkIdxInner++) {
                partitionReader.savePosition(TMP_POSITION_SLOT);
                IFrame frameInner = partitionReader.nextFrame(false);
                tAccess2.reset(frameInner.getBuffer());

                int tBeginIdxInner;
                if (tBeginIdxInnerStart < 0) {
                    tBeginIdxInner = getTupleBeginIdx(chunkIdxInner);
                } else {
                    tBeginIdxInner = tBeginIdxInnerStart;
                    tBeginIdxInnerStart = -1;
                }
                int tEndIdxInner = getTupleEndIdx(chunkIdxInner);

                for (int tIdxInner = tBeginIdxInner; tIdxInner <= tEndIdxInner; tIdxInner++) {
                    tRef2.reset(tAccess2, tIdxInner);

                    if (frameStartExists || frameEndExists) {
                        evaluate(frameValueEvals, tRef2, frameValuePointables);
                        if (frameStartExists) {
                            if (frameValueComparators.compare(frameValuePointables, frameStartPointables) < 0) {
                                // skip if value < start
                                continue;
                            }
                            // inside the frame
                            if (chunkIdxFrameStartLocal < 0) {
                                // save position of the first tuple in this frame
                                // will continue from it in the next frame iteration
                                chunkIdxFrameStartLocal = chunkIdxInner;
                                tBeginIdxFrameStartLocal = tIdxInner;
                                partitionReader.copyPosition(TMP_POSITION_SLOT, FRAME_POSITION_SLOT);
                            }
                        }
                        if (frameEndExists
                                && frameValueComparators.compare(frameValuePointables, frameEndPointables) > 0) {
                            // value > end => beyond the frame end
                            // exit the frame loop
                            break frame_loop;
                        }
                    }
                    if (frameExcludeExists && isExcluded()) {
                        // skip if excluded
                        continue;
                    }

                    if (toSkip > 0) {
                        // skip if offset hasn't been reached
                        toSkip--;
                        continue;
                    }

                    if (toWrite != 0) {
                        nestedAggAggregate(tAccess2, tIdxInner);
                    }
                    if (toWrite > 0) {
                        toWrite--;
                    }
                    if (toWrite == 0) {
                        break frame_loop;
                    }
                }
            }

            if (frameStartIsMonotonic) {
                if (chunkIdxFrameStartLocal >= 0) {
                    chunkIdxFrameStartGlobal = chunkIdxFrameStartLocal;
                    tBeginIdxFrameStartGlobal = tBeginIdxFrameStartLocal;
                } else {
                    // frame start not found, set it beyond the last chunk
                    chunkIdxFrameStartGlobal = nChunks;
                    tBeginIdxFrameStartGlobal = 0;
                }
            }

            nestedAggOutputFinalResult(tupleBuilder);
            appendToFrameFromTupleBuilder(tupleBuilder);
        }

        partitionReader.restorePosition(PARTITION_POSITION_SLOT);
    }

    private boolean isExcluded() throws HyracksDataException {
        for (int i = 0; i < frameExcludeEvals.length; i++) {
            frameExcludeEvals[i].evaluate(tRef2, frameExcludePointable2);
            boolean b = DataUtils.compare(frameExcludePointables.getField(i), frameExcludePointable2,
                    frameExcludeComparators[i]) != 0;
            if (i >= frameExcludeNegationStartIdx) {
                b = !b;
            }
            if (b) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected int getPartitionReaderSlotCount() {
        return PARTITION_READER_SLOT_COUNT;
    }
}