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
package org.apache.giraph.debugger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.giraph.conf.GiraphConfiguration;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.Computation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.utils.ReflectionUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.log4j.Logger;

/**
 * This class is used by programmers to configure what they want to be debugged.
 * Programmers can either extend this class and implement their own debug
 * configurations or use a few hadoop config parameters to use this one. If
 * programmers implement their own config, they can do the following:
 * <ul>
 * <li>Configure which vertices to debug by looking at the whole {@link Vertex}
 * object.
 * <li>Configure which supersteps to debug.
 * <li>Add a message integrity constraint by setting
 * {@link #shouldCheckMessageIntegrity()} to true and then overriding
 * {@link #isMessageCorrect(WritableComparable, WritableComparable, Writable)}.
 * <li>Add a vertex value integrity constraint by setting
 * {@link #shouldCheckVertexValueIntegrity()} and then overriding
 * {@link #isVertexValueCorrect(WritableComparable, Writable)}.
 * </ul>
 *
 * If instead the programmers use this class without extending it, they can
 * configure it as follows:
 * <ul>
 * <li>By passing -D{@link #VERTICES_TO_DEBUG_FLAG}=v1,v2,..,vn, specify a set
 * of integer or long vertex IDs to debug. The {@link Computation} class has to
 * have either a {@link LongWritable} or {@link IntWritable}. By default no
 * vertices are debugged.
 * <li>By passing -D{@link #DEBUG_NEIGHBORS_FLAG}=true/false specify whether the
 * in-neighbors of vertices that were configured to be debugged should also be
 * debugged. By default this flag is set to false.
 * <li>By passing -D{@link #SUPERSTEPS_TO_DEBUG_FLAG}=s1,s2,...,sm specify a set
 * of supersteps to debug. By default all supersteps are debugged.
 * </ul>
 *
 * Note that if programmers use this class directly, then by default the
 * debugger will capture exceptions.
 *
 * @param <I>
 *          Vertex id
 * @param <V>
 *          Vertex data
 * @param <E>
 *          Edge data
 * @param <M1>
 *          Incoming message type
 * @param <M2>
 *          Outgoing message type
 */
@SuppressWarnings({ "rawtypes" })
public class DebugConfig<I extends WritableComparable, V extends Writable,
  E extends Writable, M1 extends Writable, M2 extends Writable> {

  /**
   * String constant for splitting the parameter specifying which
   * supersteps should be debugged.
   */
  private static String SUPERSTEP_DELIMITER = ":";
  /**
   * String constant for splitting the parameter specifying which
   * vertices should be debugged.
   */
  private static final String VERTEX_ID_DELIMITER = ":";

  /**
   * String constant for specifying the subset of vertices to debug
   * when the user chooses not to debug all vertices
   */
  private static final String VERTICES_TO_DEBUG_FLAG =
     "giraph.debugger.verticesToDebug";
  /**
   * String constant for specifying whether the neighbors of specified
   * vertices should be debugged.
   */
  private static final String DEBUG_NEIGHBORS_FLAG =
    "giraph.debugger.debugNeighbors";
  /**
   * String constant for specifying the subset of supersteps to debug
   * when the user chooses not to debug the vertices in all supersteps.
   */
  private static final String SUPERSTEPS_TO_DEBUG_FLAG =
    "giraph.debugger.superstepsToDebug";
  /**
   * String constant for specifying whether all vertices should be debugged.
   */
  private static final String DEBUG_ALL_VERTICES_FLAG =
    "giraph.debugger.debugAllVertices";

  /**
   * Logger for this class.
   */
  private static final Logger LOG = Logger.getLogger(DebugConfig.class);

  /**
   * Stores the set of specified vertices to debug, when VERTICES_TO_DEBUG_FLAG
   * is specified.
   */
  private Set<I> verticesToDebugSet;
  /**
   * Stores the set of specified supersteps to debug in, when
   * SUPERSTEPS_TO_DEBUG_FLAG is specified.
   */
  private Set<Long> superstepsToDebugSet;
  /**
   * Whether the user has specified to debug the neighbors of the vertices
   * that have been specified to be debugged, i.e. whether DEBUG_NEIGHBORS_FLAG
   * is set to true.
   */
  private boolean debugNeighborsOfVerticesToDebug;
  /**
   * Whether the user has specified to debug all vertices, i.e., whether
   * DEBUG_ALL_VERTICES_FLAG is set to true.
   */
  private boolean debugAllVertices = false;

  /**
   * Default public constructor. Configures not to debug any vertex in
   * any superstep. But below {#link {@link #shouldCatchExceptions()} returns
   * true by default, so configures Graft to only catch exceptions.
   */
  public DebugConfig() {
    verticesToDebugSet = null;
    debugAllVertices = false;
    debugNeighborsOfVerticesToDebug = false;
    superstepsToDebugSet = null;
  }

  /**
   * Configures this class through a {@link GiraphConfiguration}, which may
   * contain some flags passed in by the user.
   * @param config a {@link GiraphConfiguration} object.
   */
  @SuppressWarnings("unchecked")
  public final void readConfig(GiraphConfiguration config) {
    this.debugNeighborsOfVerticesToDebug = config.getBoolean(
      DEBUG_NEIGHBORS_FLAG, false);

    String superstepsToDebugStr = config.get(SUPERSTEPS_TO_DEBUG_FLAG, null);
    if (superstepsToDebugStr == null) {
      superstepsToDebugSet = null;
    } else {
      String[] superstepsToDebugArray = superstepsToDebugStr
        .split(SUPERSTEP_DELIMITER);
      superstepsToDebugSet = new HashSet<>();
      for (String superstepStr : superstepsToDebugArray) {
        superstepsToDebugSet.add(Long.valueOf(superstepStr));
      }
    }

    debugAllVertices = config.getBoolean(DEBUG_ALL_VERTICES_FLAG, false);
    if (!debugAllVertices) {
      String verticesToDebugStr = config.get(VERTICES_TO_DEBUG_FLAG, null);
      Class<? extends Computation> userComputationClass = config
        .getComputationClass();
      Class<?>[] typeArguments = ReflectionUtils.getTypeArguments(
        Computation.class, userComputationClass);
      Class<?> idType = typeArguments[0];
      if (verticesToDebugStr != null) {
        String[] verticesToDebugArray = verticesToDebugStr
          .split(VERTEX_ID_DELIMITER);
        this.verticesToDebugSet = new HashSet<>();
        for (String idString : verticesToDebugArray) {
          if (LongWritable.class.isAssignableFrom(idType)) {
            verticesToDebugSet
              .add((I) new LongWritable(Long.valueOf(idString)));
          } else if (IntWritable.class.isAssignableFrom(idType)) {
            verticesToDebugSet.add((I) new IntWritable(Integer
              .valueOf(idString)));
          } else {
            throw new IllegalArgumentException(
              "When using the giraph.debugger.verticesToDebug argument, the " +
                "vertex IDs of the computation class needs to be LongWritable" +
                " or IntWritable.");
          }
        }
      }
    }
    LOG.debug("DebugConfig" + this);
  }

  /**
   * Whether vertices should be debugged in the specified superstep.
   * @param superstepNo superstep number.
   * @return whether the superstep should be debugged.
   */
  public boolean shouldDebugSuperstep(long superstepNo) {
    return superstepsToDebugSet == null ||
      superstepsToDebugSet.contains(superstepNo);
  }

  /**
   * Whether the specified vertex should be debugged.
   * @param vertex a vertex.
   * @return whether the vertex should be debugged.
   */
  public boolean shouldDebugVertex(Vertex<I, V, E> vertex) {
    if (debugAllVertices) {
      return true;
    }
    // Should not debug all vertices. Check if any vertices were special cased.
    if (verticesToDebugSet == null) {
      return false;
    } else {
      return verticesToDebugSet.contains(vertex.getId()) ||
        debugNeighborsOfVerticesToDebug &&
        isVertexANeighborOfAVertexToDebug(vertex);
    }
  }

  /**
   * Whether the given vertex is a neighbor of a vertex that has been
   * configured to be debugged. If so then the given vertex will also
   * be debugged.
   * @param vertex a vertex.
   * @return whether the vertex is a neighbor of vertex that should be
   * debugged.
   */
  private boolean isVertexANeighborOfAVertexToDebug(Vertex<I, V, E> vertex) {
    for (Edge<I, E> edge : vertex.getEdges()) {
      if (verticesToDebugSet.contains(edge.getTargetVertexId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return whether exceptions should be caught.
   */
  public boolean shouldCatchExceptions() {
    return true;
  }

  /**
   * @return whether message integrity constraints should be checked, i.e.,
   * whether Graft should call the {@link #isMessageCorrect(WritableComparable,
   * WritableComparable, Writable)} method on this message.
   */
  public boolean shouldCheckMessageIntegrity() {
    return false;
  }

  /**
   * @param srcId source id of the message.
   * @param dstId destination id of the message.
   * @param message message sent between srcId and dstId.
   * @return whether this message is correct, i.e, does not violate a
   * constraint.
   */
  public boolean isMessageCorrect(I srcId, I dstId, M1 message) {
    return true;
  }

  /**
   * @return whether a vertex value integrity constraints should be checked,
   * i.e., whether Graft should call the {@link #isVertexValueCorrect(
   * WritableComparable, Writable) method on this vertex.
   */
  public boolean shouldCheckVertexValueIntegrity() {
    return false;
  }

  /**
   * @param vertexId id of the vertex.
   * @param value value of the vertex.
   * @return whether this vertex's value is correct, i.e, does not violate a
   * constraint.
   */
  public boolean isVertexValueCorrect(I vertexId, V value) {
    return true;
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("superstepsToDebug: " +
      (superstepsToDebugSet == null ? "all supersteps" : Arrays
        .toString(superstepsToDebugSet.toArray())));
    stringBuilder.append("verticesToDebug: " +
      (verticesToDebugSet == null ? null : Arrays.toString(verticesToDebugSet
        .toArray())));
    stringBuilder.append("debugNeighborsOfVerticesToDebug: " +
      debugNeighborsOfVerticesToDebug);
    stringBuilder.append("shouldCatchExceptions: " + shouldCatchExceptions());
    stringBuilder.append("shouldCheckMessageIntegrity: " +
      shouldCheckMessageIntegrity());
    stringBuilder.append("shouldCheckVertexValueIntegrity: " +
      shouldCheckVertexValueIntegrity());
    return stringBuilder.toString();
  }
}