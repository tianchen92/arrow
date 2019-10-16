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

package org.apache.arrow.vector.compare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.ZeroVector;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.StructVector;
import org.apache.arrow.vector.complex.UnionVector;
import org.apache.arrow.vector.complex.impl.NullableStructWriter;
import org.apache.arrow.vector.complex.impl.UnionFixedSizeListWriter;
import org.apache.arrow.vector.complex.impl.UnionListWriter;
import org.apache.arrow.vector.holders.NullableFloat4Holder;
import org.apache.arrow.vector.holders.NullableFloat8Holder;
import org.apache.arrow.vector.holders.NullableIntHolder;
import org.apache.arrow.vector.holders.NullableUInt4Holder;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.Types;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestRangeEqualsVisitor {

  private BufferAllocator allocator;

  @Before
  public void init() {
    allocator = new RootAllocator(Long.MAX_VALUE);
  }

  private static final Charset utf8Charset = Charset.forName("UTF-8");
  private static final byte[] STR1 = "AAAAA1".getBytes(utf8Charset);
  private static final byte[] STR2 = "BBBBBBBBB2".getBytes(utf8Charset);
  private static final byte[] STR3 = "CCCC3".getBytes(utf8Charset);

  @After
  public void terminate() throws Exception {
    allocator.close();
  }

  @Test
  public void testIntVectorEqualsWithNull() {
    try (final IntVector vector1 = new IntVector("int", allocator);
        final IntVector vector2 = new IntVector("int", allocator)) {

      vector1.allocateNew(2);
      vector1.setValueCount(2);
      vector2.allocateNew(2);
      vector2.setValueCount(2);

      vector1.setSafe(0, 1);
      vector1.setSafe(1, 2);

      vector2.setSafe(0, 1);

      assertFalse(VectorEqualsVisitor.vectorEquals(vector1, vector2));
    }
  }

  @Test
  public void testEqualsWithTypeChange() {
    try (final IntVector vector1 = new IntVector("intVector1", allocator);
         final IntVector vector2 = new IntVector("intVector2", allocator);
         final BigIntVector vector3 = new BigIntVector("bigIntVector", allocator)) {

      vector1.allocateNew(2);
      vector1.setValueCount(2);
      vector2.allocateNew(2);
      vector2.setValueCount(2);

      vector1.setSafe(0, 1);
      vector1.setSafe(1, 2);

      vector2.setSafe(0, 1);
      vector2.setSafe(1, 2);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      Range range = new Range(0, 0, 2);
      assertTrue(vector1.accept(visitor, range));
      // visitor left vector changed, will reset and check type again
      assertFalse(vector3.accept(visitor, range));
    }
  }

  @Test
  public void testBaseFixedWidthVectorRangeEqual() {
    try (final IntVector vector1 = new IntVector("int", allocator);
        final IntVector vector2 = new IntVector("int", allocator)) {

      vector1.allocateNew(5);
      vector1.setValueCount(5);
      vector2.allocateNew(5);
      vector2.setValueCount(5);

      vector1.setSafe(0, 1);
      vector1.setSafe(1, 2);
      vector1.setSafe(2, 3);
      vector1.setSafe(3, 4);
      vector1.setSafe(4, 5);

      vector2.setSafe(0, 11);
      vector2.setSafe(1, 2);
      vector2.setSafe(2,3);
      vector2.setSafe(3,4);
      vector2.setSafe(4,55);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      assertTrue(visitor.rangeEquals(new Range(1, 1, 3)));
    }
  }

  @Test
  public void testBaseVariableVectorRangeEquals() {
    try (final VarCharVector vector1 = new VarCharVector("varchar", allocator);
        final VarCharVector vector2 = new VarCharVector("varchar", allocator)) {

      vector1.allocateNew();
      vector2.allocateNew();

      // set some values
      vector1.setSafe(0, STR1, 0, STR1.length);
      vector1.setSafe(1, STR2, 0, STR2.length);
      vector1.setSafe(2, STR3, 0, STR3.length);
      vector1.setSafe(3, STR2, 0, STR2.length);
      vector1.setSafe(4, STR1, 0, STR1.length);
      vector1.setValueCount(5);

      vector2.setSafe(0, STR1, 0, STR1.length);
      vector2.setSafe(1, STR2, 0, STR2.length);
      vector2.setSafe(2, STR3, 0, STR3.length);
      vector2.setSafe(3, STR2, 0, STR2.length);
      vector2.setSafe(4, STR1, 0, STR1.length);
      vector2.setValueCount(5);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      assertTrue(visitor.rangeEquals(new Range(1, 1, 3)));
    }
  }

  @Test
  public void testListVectorRangeEquals() {
    try (final ListVector vector1 = ListVector.empty("list", allocator);
        final ListVector vector2 = ListVector.empty("list", allocator);) {

      UnionListWriter writer1 = vector1.getWriter();
      writer1.allocate();

      //set some values
      writeListVector(writer1, new int[] {1, 2});
      writeListVector(writer1, new int[] {3, 4});
      writeListVector(writer1, new int[] {5, 6});
      writeListVector(writer1, new int[] {7, 8});
      writeListVector(writer1, new int[] {9, 10});
      writer1.setValueCount(5);

      UnionListWriter writer2 = vector2.getWriter();
      writer2.allocate();

      //set some values
      writeListVector(writer2, new int[] {0, 0});
      writeListVector(writer2, new int[] {3, 4});
      writeListVector(writer2, new int[] {5, 6});
      writeListVector(writer2, new int[] {7, 8});
      writeListVector(writer2, new int[] {0, 0});
      writer2.setValueCount(5);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      assertTrue(visitor.rangeEquals(new Range(1, 1, 3)));
    }
  }

  @Test
  public void testFixedSizeListVectorRangeEquals() {
    try (final FixedSizeListVector vector1 = FixedSizeListVector.empty("list", 2, allocator);
         final FixedSizeListVector vector2 = FixedSizeListVector.empty("list", 2, allocator);) {

      UnionFixedSizeListWriter writer1 = vector1.getWriter();
      writer1.allocate();

      //set some values
      writeFixedSizeListVector(writer1, new int[] {1, 2});
      writeFixedSizeListVector(writer1, new int[] {3, 4});
      writeFixedSizeListVector(writer1, new int[] {5, 6});
      writeFixedSizeListVector(writer1, new int[] {7, 8});
      writeFixedSizeListVector(writer1, new int[] {9, 10});
      writer1.setValueCount(5);

      UnionFixedSizeListWriter writer2 = vector2.getWriter();
      writer2.allocate();

      //set some values
      writeFixedSizeListVector(writer2, new int[] {0, 0});
      writeFixedSizeListVector(writer2, new int[] {3, 4});
      writeFixedSizeListVector(writer2, new int[] {5, 6});
      writeFixedSizeListVector(writer2, new int[] {7, 8});
      writeFixedSizeListVector(writer2, new int[] {0, 0});
      writer2.setValueCount(5);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      assertTrue(visitor.rangeEquals(new Range(1, 1, 3)));
      assertFalse(visitor.rangeEquals(new Range(0, 0, 5)));
    }
  }

  @Test
  public void testStructVectorRangeEquals() {
    try (final StructVector vector1 = StructVector.empty("struct", allocator);
        final StructVector vector2 = StructVector.empty("struct", allocator);) {
      vector1.addOrGet("f0", FieldType.nullable(new ArrowType.Int(32, true)), IntVector.class);
      vector1.addOrGet("f1", FieldType.nullable(new ArrowType.Int(64, true)), BigIntVector.class);
      vector2.addOrGet("f0", FieldType.nullable(new ArrowType.Int(32, true)), IntVector.class);
      vector2.addOrGet("f1", FieldType.nullable(new ArrowType.Int(64, true)), BigIntVector.class);

      NullableStructWriter writer1 = vector1.getWriter();
      writer1.allocate();

      writeStructVector(writer1, 1, 10L);
      writeStructVector(writer1, 2, 20L);
      writeStructVector(writer1, 3, 30L);
      writeStructVector(writer1, 4, 40L);
      writeStructVector(writer1, 5, 50L);
      writer1.setValueCount(5);

      NullableStructWriter writer2 = vector2.getWriter();
      writer2.allocate();

      writeStructVector(writer2, 0, 00L);
      writeStructVector(writer2, 2, 20L);
      writeStructVector(writer2, 3, 30L);
      writeStructVector(writer2, 4, 40L);
      writeStructVector(writer2, 0, 0L);
      writer2.setValueCount(5);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      assertTrue(visitor.rangeEquals(new Range(1, 1, 3)));
    }
  }

  @Test
  public void testUnionVectorRangeEquals() {
    try (final UnionVector vector1 = new UnionVector("union", allocator, null);
        final UnionVector vector2 = new UnionVector("union", allocator, null);) {

      final NullableUInt4Holder uInt4Holder = new NullableUInt4Holder();
      uInt4Holder.value = 10;
      uInt4Holder.isSet = 1;

      final NullableIntHolder intHolder = new NullableIntHolder();
      uInt4Holder.value = 20;
      uInt4Holder.isSet = 1;

      vector1.setType(0, Types.MinorType.UINT4);
      vector1.setSafe(0, uInt4Holder);

      vector1.setType(1, Types.MinorType.INT);
      vector1.setSafe(1, intHolder);

      vector1.setType(2, Types.MinorType.INT);
      vector1.setSafe(2, intHolder);
      vector1.setValueCount(3);

      vector2.setType(0, Types.MinorType.UINT4);
      vector2.setSafe(0, uInt4Holder);

      vector2.setType(1, Types.MinorType.INT);
      vector2.setSafe(1, intHolder);

      vector2.setType(2, Types.MinorType.INT);
      vector2.setSafe(2, intHolder);
      vector2.setValueCount(3);

      RangeEqualsVisitor visitor = new RangeEqualsVisitor(vector1, vector2);
      assertTrue(visitor.rangeEquals(new Range(1, 1, 2)));
    }
  }

  @Ignore
  @Test
  public void testEqualsWithOutTypeCheck() {
    try (final IntVector intVector = new IntVector("int", allocator);
        final ZeroVector zeroVector = new ZeroVector()) {

      assertTrue(VectorEqualsVisitor.vectorEquals(intVector, zeroVector, false));
      assertTrue(VectorEqualsVisitor.vectorEquals(zeroVector, intVector, false));
    }
  }

  @Test
  public void testFloat4ApproxEquals() {
    try (final Float4Vector vector1 = new Float4Vector("float", allocator);
         final Float4Vector vector2 = new Float4Vector("float", allocator);
         final Float4Vector vector3 = new Float4Vector("float", allocator)) {

      vector1.allocateNew(2);
      vector1.setValueCount(2);
      vector2.allocateNew(2);
      vector2.setValueCount(2);
      vector3.allocateNew(2);
      vector3.setValueCount(2);

      vector1.setSafe(0, 1.1f);
      vector1.setSafe(1, 2.2f);

      float epsilon = 1.0E-6f;

      vector2.setSafe(0, 1.1f + epsilon / 2);
      vector2.setSafe(1, 2.2f + epsilon / 2);

      vector3.setSafe(0, 1.1f + epsilon * 2);
      vector3.setSafe(1, 2.2f + epsilon * 2);

      Range range = new Range(0, 0, vector1.getValueCount());

      ApproxEqualsVisitor visitor12 = new ApproxEqualsVisitor(vector1, vector2, epsilon, epsilon);
      assertTrue(visitor12.rangeEquals(range));

      ApproxEqualsVisitor visitor13 = new ApproxEqualsVisitor(vector1, vector3, epsilon, epsilon);
      assertFalse(visitor13.rangeEquals(range));
    }
  }

  @Test
  public void testFloat8ApproxEquals() {
    try (final Float8Vector vector1 = new Float8Vector("float", allocator);
        final Float8Vector vector2 = new Float8Vector("float", allocator);
        final Float8Vector vector3 = new Float8Vector("float", allocator)) {

      vector1.allocateNew(2);
      vector1.setValueCount(2);
      vector2.allocateNew(2);
      vector2.setValueCount(2);
      vector3.allocateNew(2);
      vector3.setValueCount(2);

      vector1.setSafe(0, 1.1);
      vector1.setSafe(1, 2.2);

      float epsilon = 1.0E-6f;

      vector2.setSafe(0, 1.1 + epsilon / 2);
      vector2.setSafe(1, 2.2 + epsilon / 2);

      vector3.setSafe(0, 1.1 + epsilon * 2);
      vector3.setSafe(1, 2.2 + epsilon * 2);

      Range range = new Range(0, 0, vector1.getValueCount());
      assertTrue(new ApproxEqualsVisitor(vector1, vector2, epsilon, epsilon).rangeEquals(range));
      assertFalse(new ApproxEqualsVisitor(vector1, vector3, epsilon, epsilon).rangeEquals(range));
    }
  }

  @Test
  public void testStructVectorApproxEquals() {
    try (final StructVector right = StructVector.empty("struct", allocator);
        final StructVector left1 = StructVector.empty("struct", allocator);
        final StructVector left2 = StructVector.empty("struct", allocator);
        ) {
      right.addOrGet("f0",
          FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), Float4Vector.class);
      right.addOrGet("f1",
          FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), Float8Vector.class);
      left1.addOrGet("f0",
          FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), Float4Vector.class);
      left1.addOrGet("f1",
          FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), Float8Vector.class);
      left2.addOrGet("f0",
          FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), Float4Vector.class);
      left2.addOrGet("f1",
          FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), Float8Vector.class);

      final float epsilon = 1.0E-6f;

      NullableStructWriter rightWriter = right.getWriter();
      rightWriter.allocate();
      writeStructVector(rightWriter, 1.1f, 2.2);
      writeStructVector(rightWriter, 2.02f, 4.04);
      rightWriter.setValueCount(2);

      NullableStructWriter leftWriter1 = left1.getWriter();
      leftWriter1.allocate();
      writeStructVector(leftWriter1, 1.1f + epsilon / 2, 2.2 + epsilon / 2);
      writeStructVector(leftWriter1, 2.02f - epsilon / 2, 4.04 - epsilon / 2);
      leftWriter1.setValueCount(2);

      NullableStructWriter leftWriter2 = left2.getWriter();
      leftWriter2.allocate();
      writeStructVector(leftWriter2, 1.1f + epsilon * 2, 2.2 + epsilon * 2);
      writeStructVector(leftWriter2, 2.02f - epsilon * 2, 4.04 - epsilon * 2);
      leftWriter2.setValueCount(2);

      Range range = new Range(0, 0, right.getValueCount());
      assertTrue(new ApproxEqualsVisitor(left1, right, epsilon, epsilon).rangeEquals(range));
      assertFalse(new ApproxEqualsVisitor(left2, right, epsilon, epsilon).rangeEquals(range));
    }
  }

  @Test
  public void testUnionVectorApproxEquals() {
    try (final UnionVector right = new UnionVector("union", allocator, null);
         final UnionVector left1 = new UnionVector("union", allocator, null);
         final UnionVector left2 = new UnionVector("union", allocator, null);) {

      final NullableFloat4Holder float4Holder = new NullableFloat4Holder();
      float4Holder.value = 1.01f;
      float4Holder.isSet = 1;

      final NullableFloat8Holder float8Holder = new NullableFloat8Holder();
      float8Holder.value = 2.02f;
      float8Holder.isSet = 1;

      final float epsilon = 1.0E-6f;

      right.setType(0, Types.MinorType.FLOAT4);
      right.setSafe(0, float4Holder);
      right.setType(1, Types.MinorType.FLOAT8);
      right.setSafe(1, float8Holder);
      right.setValueCount(2);

      float4Holder.value += epsilon / 2;
      float8Holder.value += epsilon / 2;

      left1.setType(0, Types.MinorType.FLOAT4);
      left1.setSafe(0, float4Holder);
      left1.setType(1, Types.MinorType.FLOAT8);
      left1.setSafe(1, float8Holder);
      left1.setValueCount(2);

      float4Holder.value += epsilon * 2;
      float8Holder.value += epsilon * 2;

      left2.setType(0, Types.MinorType.FLOAT4);
      left2.setSafe(0, float4Holder);
      left2.setType(1, Types.MinorType.FLOAT8);
      left2.setSafe(1, float8Holder);
      left2.setValueCount(2);

      Range range = new Range(0, 0, right.getValueCount());
      assertTrue(new ApproxEqualsVisitor(left1, right, epsilon, epsilon).rangeEquals(range));
      assertFalse(new ApproxEqualsVisitor(left2, right, epsilon, epsilon).rangeEquals(range));
    }
  }

  @Test
  public void testListVectorApproxEquals() {
    try (final ListVector right = ListVector.empty("list", allocator);
         final ListVector left1 = ListVector.empty("list", allocator);
         final ListVector left2 = ListVector.empty("list", allocator);) {

      final float epsilon = 1.0E-6f;

      UnionListWriter rightWriter = right.getWriter();
      rightWriter.allocate();
      writeListVector(rightWriter, new double[] {1, 2});
      writeListVector(rightWriter, new double[] {1.01, 2.02});
      rightWriter.setValueCount(2);

      UnionListWriter leftWriter1 = left1.getWriter();
      leftWriter1.allocate();
      writeListVector(leftWriter1, new double[] {1, 2});
      writeListVector(leftWriter1, new double[] {1.01 + epsilon / 2, 2.02 - epsilon / 2});
      leftWriter1.setValueCount(2);

      UnionListWriter leftWriter2 = left2.getWriter();
      leftWriter2.allocate();
      writeListVector(leftWriter2, new double[] {1, 2});
      writeListVector(leftWriter2, new double[] {1.01 + epsilon * 2, 2.02 - epsilon * 2});
      leftWriter2.setValueCount(2);

      Range range = new Range(0, 0, right.getValueCount());
      assertTrue(new ApproxEqualsVisitor(left1, right, epsilon, epsilon).rangeEquals(range));
      assertFalse(new ApproxEqualsVisitor(left2, right, epsilon, epsilon).rangeEquals(range));
    }
  }

  private void writeStructVector(NullableStructWriter writer, int value1, long value2) {
    writer.start();
    writer.integer("f0").writeInt(value1);
    writer.bigInt("f1").writeBigInt(value2);
    writer.end();
  }

  private void writeStructVector(NullableStructWriter writer, float value1, double value2) {
    writer.start();
    writer.float4("f0").writeFloat4(value1);
    writer.float8("f1").writeFloat8(value2);
    writer.end();
  }

  private void writeListVector(UnionListWriter writer, int[] values) {
    writer.startList();
    for (int v: values) {
      writer.integer().writeInt(v);
    }
    writer.endList();
  }

  private void writeFixedSizeListVector(UnionFixedSizeListWriter writer, int[] values) {
    writer.startList();
    for (int v: values) {
      writer.integer().writeInt(v);
    }
    writer.endList();
  }

  private void writeListVector(UnionListWriter writer, double[] values) {
    writer.startList();
    for (double v: values) {
      writer.float8().writeFloat8(v);
    }
    writer.endList();
  }
}
