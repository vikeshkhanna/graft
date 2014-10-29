/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.giraph.debugger.examples;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import com.google.common.collect.Lists;

/**
 * Simple text-based {@link org.apache.giraph.io.VertexInputFormat} for
 * double weighted graphs with long ids.
 *
 * Each line consists of: vertex neighbor1 weight1 neighbor2 weight2 ...
 */
public class TextAdjacencyListLongIDDoubleEdgeWeightInputFormat<
  V extends Writable> extends TextVertexInputFormat<
  LongWritable, V, DoubleWritable> {
  /** Separator of the vertex and neighbors */
  private static final Pattern SEPARATOR = Pattern.compile("[\t ]");

  @Override
  public TextVertexReader createVertexReader(InputSplit split,
                                             TaskAttemptContext context)
    throws IOException {
    return new LongIDDoubleEdgeWeightVertexReader();
  }

  /**
   * Vertex reader associated with
   * {@link TextAdjacencyListLongIDDoubleEdgeWeightInputFormat}.
   */
  public class LongIDDoubleEdgeWeightVertexReader extends
      TextVertexReaderFromEachLineProcessed<String[]> {
    /** Cached vertex id for the current line */
    private LongWritable id;

    @Override
    protected String[] preprocessLine(Text line) throws IOException {
      String[] tokens = SEPARATOR.split(line.toString());
      id = new LongWritable(Long.parseLong(tokens[0]));
      return tokens;
    }

    @Override
    protected LongWritable getId(String[] tokens) throws IOException {
      return id;
    }

    @Override
    protected V getValue(String[] tokens) throws IOException {
      return getConf().createVertexValue();
    }

    @Override
    protected Iterable<Edge<LongWritable, DoubleWritable>> getEdges(
        String[] tokens) throws IOException {
      List<Edge<LongWritable, DoubleWritable>> edges =
          Lists.newArrayListWithCapacity((tokens.length - 1)/2);
      for (int n = 1; n < tokens.length;) {
        edges.add(EdgeFactory.create(
          new LongWritable(Long.parseLong(tokens[n])),
            new DoubleWritable(Double.parseDouble(tokens[n+1]))));
        n += 2;
      }
      return edges;
    }
  }
}
