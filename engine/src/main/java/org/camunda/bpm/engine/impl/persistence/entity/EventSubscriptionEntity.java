/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.persistence.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.HasDbRevision;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.event.EventHandler;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.ProcessEventJobHandler;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.EventSubscription;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;

/**
 * @author Daniel Meyer
 */
public abstract class EventSubscriptionEntity implements EventSubscription, DbEntity, HasDbRevision, Serializable {

  private static final long serialVersionUID = 1L;
  
  // persistent state ///////////////////////////
  protected String id;
  protected int revision = 1;
  protected String eventType;
  protected String eventName;
  protected String executionId;
  protected String processInstanceId;
  protected String activityId;
  protected String configuration;
  protected Date created;
  
  // runtime state /////////////////////////////
  protected ExecutionEntity execution;
  protected ActivityImpl activity;  
  
  /////////////////////////////////////////////
  
  public EventSubscriptionEntity() { 
    this.created = ClockUtil.getCurrentTime();
  }

  public EventSubscriptionEntity(ExecutionEntity executionEntity) {
    this();
    setExecution(executionEntity);
    setActivity(execution.getActivity());
    this.processInstanceId = executionEntity.getProcessInstanceId();
  }
  
  // processing /////////////////////////////
  
  public void eventReceived(Serializable payload, boolean processASync) {
    if(processASync) {
      scheduleEventAsync(payload);
    } else {
      processEventSync(payload);
    }
  }
  
  protected void processEventSync(Object payload) {
    EventHandler eventHandler = Context.getProcessEngineConfiguration().getEventHandler(eventType);
    ensureNotNull("Could not find eventhandler for event of type '" + eventType + "'", "eventHandler", eventHandler);
    eventHandler.handleEvent(this, payload, Context.getCommandContext());
  }
  
  protected void scheduleEventAsync(Serializable payload) {
    
    final CommandContext commandContext = Context.getCommandContext();

    MessageEntity message = new MessageEntity();
    message.setJobHandlerType(ProcessEventJobHandler.TYPE);
    message.setJobHandlerConfiguration(id);

    // TODO: support payload
//    if(payload != null) {
//      message.setEventPayload(payload);
//    }
    
    commandContext.getJobManager().send(message);
  }
  
  // persistence behavior /////////////////////

  public void delete() {
    Context.getCommandContext()
      .getEventSubscriptionManager()
      .deleteEventSubscription(this);
    removeFromExecution();
  }
  
  public void insert() {
    Context.getCommandContext()
      .getEventSubscriptionManager()
      .insert(this);
    addToExecution();   
  }
  
 // referential integrity -> ExecutionEntity ////////////////////////////////////
  
  protected void addToExecution() {
    // add reference in execution
    ExecutionEntity execution = getExecution();
    if(execution != null) {
      execution.addEventSubscription(this);
    }
  }
  
  protected void removeFromExecution() {
    // remove reference in execution
    ExecutionEntity execution = getExecution();
    if(execution != null) {
      execution.removeEventSubscription(this);
    }
  }
  
  public Object getPersistentState() {
    HashMap<String, Object> persistentState = new HashMap<String, Object>();
    persistentState.put("executionId", executionId);
    persistentState.put("configuration", configuration);
    return persistentState;
  }
  
  // getters & setters ////////////////////////////
    
  public ExecutionEntity getExecution() {
    if(execution == null && executionId != null) {
      execution = Context.getCommandContext()
              .getExecutionManager()
              .findExecutionById(executionId);
    }
    return execution;
  }
    
  public void setExecution(ExecutionEntity execution) {
    this.execution = execution;
    if(execution != null) {
      this.executionId = execution.getId();
    }
  }
    
  public ActivityImpl getActivity() {
    if(activity == null && activityId != null) {
      ExecutionEntity execution = getExecution();
      if(execution != null) {
        ProcessDefinitionImpl processDefinition = execution.getProcessDefinition();
        activity = processDefinition.findActivity(activityId);
      }
    }
    return activity;
  }
    
  public void setActivity(ActivityImpl activity) {
    this.activity = activity;
    if(activity != null) {
      this.activityId = activity.getId();
    }
  }
  
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public int getRevision() {
    return revision;
  }

  public void setRevision(int revision) {
    this.revision = revision;
  }
  
  public int getRevisionNext() {
    return revision +1;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  public String getExecutionId() {
    return executionId;
  }

  public void setExecutionId(String executionId) {
    this.executionId = executionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getConfiguration() {
    return configuration;
  }

  public void setConfiguration(String configuration) {
    this.configuration = configuration;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }
  
  public Date getCreated() {
    return created;
  }
  
  public void setCreated(Date created) {
    this.created = created;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    EventSubscriptionEntity other = (EventSubscriptionEntity) obj;
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName()
           + "[id=" + id
           + ", eventType=" + eventType
           + ", eventName=" + eventName
           + ", executionId=" + executionId
           + ", processInstanceId=" + processInstanceId
           + ", activityId=" + activityId
           + ", configuration=" + configuration
           + ", revision=" + revision
           + ", created=" + created
           + "]";
  }

}
