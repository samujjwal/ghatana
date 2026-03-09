package com.ghatana.platform.governance.response;

import com.ghatana.contracts.event.v1.EventProto;

/**
 * Action that can be executed in response to governance policy triggers.
 
 *
 * @doc.type interface
 * @doc.purpose Response action
 * @doc.layer platform
 * @doc.pattern Interface
*/
public interface ResponseAction {
    /**
     * Executes the response action for the given event.
     *
     * @param event The event to process
     * @return true if the action succeeded
     */
    boolean execute(EventProto event);
}
