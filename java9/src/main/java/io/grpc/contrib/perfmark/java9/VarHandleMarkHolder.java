package io.grpc.contrib.perfmark.java9;

import io.grpc.contrib.perfmark.impl.Generator;
import io.grpc.contrib.perfmark.impl.Mark;
import io.grpc.contrib.perfmark.impl.MarkHolder;
import io.grpc.contrib.perfmark.impl.Marker;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.List;

/**
 * VarHandleMarkHolder is a MarkHolder optimized for wait free writes and few reads.
 */
final class VarHandleMarkHolder extends MarkHolder {
  private static final int MAX_EVENTS = 16384;
  private static final long MAX_EVENTS_MASK = MAX_EVENTS - 1;
  private static final long GEN_MASK = (1 << Generator.GEN_OFFSET) - 1;

  private static final long START_OP = Mark.Operation.TASK_START.ordinal();
  private static final long START_NOTAG_OP = Mark.Operation.TASK_NOTAG_START.ordinal();
  private static final long STOP_OP = Mark.Operation.TASK_END.ordinal();
  private static final long STOP_NOTAG_OP = Mark.Operation.TASK_NOTAG_END.ordinal();
  private static final long LINK_OP = Mark.Operation.LINK.ordinal();

  private static final VarHandle IDX;
  private static final VarHandle OBJECTS;
  private static final VarHandle STRINGS;
  private static final VarHandle LONGS;

  static {
    try {
      IDX = MethodHandles.lookup().findVarHandle(VarHandleMarkHolder.class, "idx", long.class);
      OBJECTS = MethodHandles.arrayElementVarHandle(Object[].class);
      STRINGS = MethodHandles.arrayElementVarHandle(String[].class);
      LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  // where to write to next
  @SuppressWarnings("unused") // Used Reflectively
  private volatile long idx;
  private final Object[] taskNameOrMarkers = new Object[MAX_EVENTS];
  private final String[] tagNames = new String[MAX_EVENTS];
  private final long[] tagIds= new long[MAX_EVENTS];
  private final long[] nanoTimes = new long[MAX_EVENTS];
  private final long[] genOps = new long[MAX_EVENTS];

  VarHandleMarkHolder() {}

  @Override
  public void start(long gen, String taskName, String tagName, long tagId, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    OBJECTS.setOpaque(taskNameOrMarkers, i, taskName);
    STRINGS.setOpaque(tagNames, i, tagName);
    LONGS.setOpaque(tagIds, i, tagId);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + START_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void start(long gen, Marker marker, String tagName, long tagId, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    OBJECTS.setOpaque(taskNameOrMarkers, i, marker);
    STRINGS.setOpaque(tagNames, i, tagName);
    LONGS.setOpaque(tagIds, i, tagId);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + START_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void start(long gen, String taskName, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    OBJECTS.setOpaque(taskNameOrMarkers, i, taskName);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + START_NOTAG_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void start(long gen, Marker marker, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    OBJECTS.setOpaque(taskNameOrMarkers, i, marker);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + START_NOTAG_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void link(long gen, long linkId, Marker marker) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    LONGS.setOpaque(tagIds, i, linkId);
    OBJECTS.setOpaque(taskNameOrMarkers, i, marker);
    LONGS.setOpaque(genOps, i, gen + LINK_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void stop(long gen, String taskName, String tagName, long tagId, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    STRINGS.setOpaque(tagNames, i, tagName);
    LONGS.setOpaque(tagIds, i, tagId);
    OBJECTS.setOpaque(taskNameOrMarkers, i, taskName);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + STOP_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void stop(long gen, Marker marker, String tagName, long tagId, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    STRINGS.setOpaque(tagNames, i, tagName);
    LONGS.setOpaque(tagIds, i, tagId);
    OBJECTS.setOpaque(taskNameOrMarkers, i, marker);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + STOP_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void stop(long gen, String taskName, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    OBJECTS.setOpaque(taskNameOrMarkers, i, taskName);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + STOP_NOTAG_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void stop(long gen, Marker marker, long nanoTime) {
    long localIdx = (long) IDX.get(this);
    int i = (int) (localIdx & MAX_EVENTS_MASK);
    VarHandle.acquireFence();
    OBJECTS.setOpaque(taskNameOrMarkers, i, marker);
    LONGS.setOpaque(nanoTimes, i, nanoTime);
    LONGS.setOpaque(genOps, i, gen + STOP_NOTAG_OP);
    IDX.setRelease(this, localIdx + 1);
  }

  @Override
  public void resetForTest() {
    Arrays.fill(taskNameOrMarkers, null);
    Arrays.fill(tagNames, null);
    Arrays.fill(tagIds, 0);
    Arrays.fill(nanoTimes, 0);
    Arrays.fill(genOps, 0);
    IDX.setVolatile(this, 0L);
  }

  @Override
  public List<Mark> read(boolean readerIsWriter) {
    final Object[] localTaskNameOrMarkers = new Object[MAX_EVENTS];
    final String[] localTagNames = new String[MAX_EVENTS];
    final long[] localTagIds= new long[MAX_EVENTS];
    final long[] localNanoTimes = new long[MAX_EVENTS];
    final long[] localGenOps = new long[MAX_EVENTS];
    long startIdx = (long) IDX.getVolatile(this);
    int size = (int) Math.min(startIdx, MAX_EVENTS);
    for (int i = 0; i < size; i++) {
      localTaskNameOrMarkers[i] = (Object) OBJECTS.getOpaque(taskNameOrMarkers, i);
      localTagNames[i] = (String) STRINGS.getOpaque(tagNames, i);
      localTagIds[i] = (long) LONGS.getOpaque(tagIds, i);
      localNanoTimes[i] = (long) LONGS.getOpaque(nanoTimes, i);
      localGenOps[i] = (long) LONGS.getOpaque(genOps, i);
    }
    long endIdx = (long) IDX.getVolatile(this);
    if (endIdx < startIdx) {
      throw new AssertionError();
    }
    // If we are reading from ourselves (such as in a test), we can assume there isn't an in
    // progress write modifying the oldest entry.  Additionally, if the writer has not yet
    // wrapped around, the last entry cannot have been corrupted.
    boolean tailValid = readerIsWriter || endIdx < MAX_EVENTS - 1;
    endIdx += !tailValid ? 1 : 0;
    long eventsToDrop = endIdx - startIdx;
    final Deque<Mark> marks = new ArrayDeque<>(size);
    for (int i = 0; i < size - eventsToDrop; i++) {
      int readIdx = (int) ((startIdx - i - 1) & MAX_EVENTS_MASK);
      long gen = localGenOps[readIdx] & ~GEN_MASK;
      Mark.Operation op = Mark.Operation.valueOf((int) (localGenOps[readIdx] & GEN_MASK));
      if (op == Mark.Operation.NONE) {
        throw new ConcurrentModificationException("Read of storage was not threadsafe");
      }
      Object taskNameOrMarker = localTaskNameOrMarkers[readIdx];
      if (taskNameOrMarker instanceof Marker) {
        marks.addFirst(Mark.create(
            (Marker) taskNameOrMarker,
            localTagNames[readIdx],
            localTagIds[readIdx],
            localNanoTimes[readIdx],
            gen,
            op));
      } else if (taskNameOrMarker instanceof String) {
        marks.addFirst(Mark.create(
            (String) taskNameOrMarker,
            localTagNames[readIdx],
            localTagIds[readIdx],
            localNanoTimes[readIdx],
            gen,
            op));
      } else {
        throw new RuntimeException("Bad marker or string " + taskNameOrMarker);
      }
    }

    return Collections.unmodifiableList(new ArrayList<>(marks));
  }
}