// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: idb.proto

package idb;

public interface InstrumentsRunRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:idb.InstrumentsRunRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>.idb.InstrumentsRunRequest.Start start = 1;</code>
   * @return Whether the start field is set.
   */
  boolean hasStart();
  /**
   * <code>.idb.InstrumentsRunRequest.Start start = 1;</code>
   * @return The start.
   */
  idb.InstrumentsRunRequest.Start getStart();
  /**
   * <code>.idb.InstrumentsRunRequest.Start start = 1;</code>
   */
  idb.InstrumentsRunRequest.StartOrBuilder getStartOrBuilder();

  /**
   * <code>.idb.InstrumentsRunRequest.Stop stop = 2;</code>
   * @return Whether the stop field is set.
   */
  boolean hasStop();
  /**
   * <code>.idb.InstrumentsRunRequest.Stop stop = 2;</code>
   * @return The stop.
   */
  idb.InstrumentsRunRequest.Stop getStop();
  /**
   * <code>.idb.InstrumentsRunRequest.Stop stop = 2;</code>
   */
  idb.InstrumentsRunRequest.StopOrBuilder getStopOrBuilder();

  public idb.InstrumentsRunRequest.ControlCase getControlCase();
}