/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.arrow.vector.ipc.message;

import org.apache.arrow.FlatBufferBuilderWrapper;
import org.apache.arrow.flatbuf.DictionaryBatch;

/**
 * POJO wrapper around a Dictionary Batch IPC messages
 * (https://arrow.apache.org/docs/format/IPC.html#dictionary-batches)
 */
public class ArrowDictionaryBatch implements ArrowMessage {

  private final long dictionaryId;
  private final ArrowRecordBatch dictionary;

  public ArrowDictionaryBatch(long dictionaryId, ArrowRecordBatch dictionary) {
    this.dictionaryId = dictionaryId;
    this.dictionary = dictionary;
  }

  public long getDictionaryId() {
    return dictionaryId;
  }

  public ArrowRecordBatch getDictionary() {
    return dictionary;
  }

  @Override
  public int writeTo(FlatBufferBuilderWrapper builderWrapper) {
    int dataOffset = dictionary.writeTo(builderWrapper);
    DictionaryBatch.startDictionaryBatch(builderWrapper.getInternalBuilder());
    DictionaryBatch.addId(builderWrapper.getInternalBuilder(), dictionaryId);
    DictionaryBatch.addData(builderWrapper.getInternalBuilder(), dataOffset);
    return DictionaryBatch.endDictionaryBatch(builderWrapper.getInternalBuilder());
  }

  @Override
  public int computeBodyLength() {
    return dictionary.computeBodyLength();
  }

  @Override
  public <T> T accepts(ArrowMessageVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public String toString() {
    return "ArrowDictionaryBatch [dictionaryId=" + dictionaryId + ", dictionary=" + dictionary + "]";
  }

  @Override
  public void close() {
    dictionary.close();
  }
}
