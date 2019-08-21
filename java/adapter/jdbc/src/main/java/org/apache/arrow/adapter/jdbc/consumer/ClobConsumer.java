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

package org.apache.arrow.adapter.jdbc.consumer;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.arrow.vector.BitVectorHelper;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.complex.impl.VarCharWriterImpl;
import org.apache.arrow.vector.complex.writer.VarCharWriter;

import io.netty.buffer.ArrowBuf;

/**
 * Consumer which consume clob type values from {@link ResultSet}.
 * Write the data to {@link org.apache.arrow.vector.VarCharVector}.
 */
public class ClobConsumer implements JdbcConsumer<VarCharVector> {

  private static final int BUFFER_SIZE = 256;

  private VarCharWriter writer;
  private VarCharVector vector;
  private final int columnIndexInResultSet;

  /**
   * Instantiate a ClobConsumer.
   */
  public ClobConsumer(VarCharVector vector, int index) {
    this.vector = vector;
    this.writer = new VarCharWriterImpl(vector);
    this.columnIndexInResultSet = index;
  }

  @Override
  public void consume(ResultSet resultSet) throws SQLException {
    Clob clob = resultSet.getClob(columnIndexInResultSet);
    int idx = writer.getPosition();
    if (!resultSet.wasNull()) {
      if (clob != null) {
        long length = clob.length();

        int read = 1;
        int readSize = length < BUFFER_SIZE ? (int) length : BUFFER_SIZE;
        int totalBytes = 0;

        ArrowBuf dataBuffer = vector.getDataBuffer();
        ArrowBuf offsetBuffer = vector.getOffsetBuffer();
        int startIndex = offsetBuffer.getInt(idx * 4);
        while (read <= length) {
          String str = clob.getSubString(read, readSize);
          byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

          dataBuffer.setBytes(startIndex + totalBytes, bytes);
          totalBytes += bytes.length;
          read += readSize;
        }
        offsetBuffer.setInt((idx + 1) * 4, startIndex + totalBytes);
        BitVectorHelper.setValidityBitToOne(vector.getValidityBuffer(), idx);
        vector.setLastSet(idx);
      }
    }
    writer.setPosition(writer.getPosition() + 1);
  }

  @Override
  public void close() throws Exception {
    writer.close();
  }

  @Override
  public void resetValueVector(VarCharVector vector) {
    this.vector = vector;
    this.writer = new VarCharWriterImpl(vector);
  }
}
