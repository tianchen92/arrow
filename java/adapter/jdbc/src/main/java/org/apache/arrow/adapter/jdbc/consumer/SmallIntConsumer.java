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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.complex.impl.SmallIntWriterImpl;
import org.apache.arrow.vector.complex.writer.SmallIntWriter;

/**
 * Consumer which consume smallInt type values from {@link ResultSet}.
 * Write the data to {@link org.apache.arrow.vector.SmallIntVector}.
 */
public class SmallIntConsumer implements JdbcConsumer {

  private final SmallIntWriter writer;
  private final int index;

  private short reuse;

  /**
   * Instantiate a SmallIntConsumer.
   */
  public SmallIntConsumer(SmallIntVector vector, int index) {
    this.writer = new SmallIntWriterImpl(vector);
    this.index = index;
  }

  @Override
  public void consume(ResultSet resultSet, Calendar calendar) throws SQLException {
    reuse = resultSet.getShort(index);
    if (resultSet.wasNull()) {
      addNull();
    } else {
      writer.writeSmallInt(reuse);
      writer.setPosition(writer.getPosition() + 1);
    }
  }

  @Override
  public void addNull() {
    writer.setPosition(writer.getPosition() + 1);
  }
}
