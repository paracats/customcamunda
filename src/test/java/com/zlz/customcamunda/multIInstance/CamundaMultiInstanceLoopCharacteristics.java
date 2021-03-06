package com.zlz.customcamunda.multIInstance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessInstanceWithVariablesImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.zlz.customcamunda.FlowEngine;
import com.zlz.customcamunda.util.PropertiesUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:camunda.cfg.xml")
public class CamundaMultiInstanceLoopCharacteristics {

	private Logger log = LoggerFactory.getLogger("CamundaBaseTest");

	@Resource
	protected FlowEngine engine;

	protected String orgId = "001";
	protected String prodefName = "001";
	protected String prodefKey = "test1";
	protected String resourceName = "./bpmn/multiInstance5.bpmn";
	protected String workFlowDefinitionText = null;
	protected String workflowDefinitionId = null;
	protected ProcessDefinition prodef = null;
	protected boolean update = true;
	private static PropertiesUtil util = new PropertiesUtil();

	@Test
	public void testFindProcessDef() {
		try {
			// 准备流程定义，加载到测试用例中
			engine.buildProcessEngine();
			log.info("初始化引擎");
			if (!update) {
				ProcessDefinition prodef = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionResourceName(resourceName).singleResult();
				if (prodef != null) {
					this.prodef = prodef;
				} else {
					deployProcessDefinition();
				}
			} else {
				deployProcessDefinition();
			}

		} catch (

		Exception e) {
			e.printStackTrace();
		}
	}

	private void deployProcessDefinition() throws FileNotFoundException {
		// 校验流程文件
		boolean validate = engine.validateBpmn(new File(resourceName));
		log.info("校验文件:{},结果:{}", resourceName, validate);
		if (validate) {
			InputStream inputStream = new FileInputStream(new File(resourceName));
			Deployment deployment = engine.getRepositoryService().createDeployment().addInputStream(resourceName, inputStream).deploy();
			String deployid = deployment.getId();
			log.info("deployid:{}", deployid);
			this.prodef = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployid).singleResult();
			log.info("初始化引擎:{}", this.prodef.getId());
		}

	}

	@Test
	public void testStartServiceProcessDef() throws FileNotFoundException {
		ini();
		Map<String, Object> variables = new HashMap<String, Object>();
		// 准备流程定义，加载到测试用例中
//		variables.put("assignee", Arrays.asList("100","200","300"));
		variables.put("assigneeList", Arrays.asList("100","200","300"));
		ProcessInstance instance = engine.getRuntimeService().startProcessInstanceById(this.prodef.getId(), variables);
		log.info("instance: {} started!", instance.getId());
		util.setPropertiesValue("instanceId", instance.getId());
		ProcessInstanceWithVariablesImpl instancetmp = (ProcessInstanceWithVariablesImpl) instance;
		List<TaskEntity> tasks = instancetmp.getExecutionEntity().getTasks();
		if (!tasks.isEmpty()) {
			TaskEntity task = tasks.get(0);
			log.info("todo task:{}", task);
			String taskId = task.getId();
			util.setPropertiesValue("taskId", taskId);
			engine.getCustomTaskService().back(taskId, variables);
		}

		showHis();
		
		TaskEntity task = (TaskEntity) engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
		if (task != null) {
			String taskId = task.getId();
			util.setPropertiesValue("taskId", taskId);
		}
	}
	
	private void ini() {
		try {
			// 准备流程定义，加载到测试用例中
			engine.buildProcessEngine();
			log.info("初始化引擎");
			if (!update) {
				ProcessDefinition prodef = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionResourceName(resourceName).singleResult();
				if (prodef != null) {
					this.prodef = prodef;
				} else {
					deployProcessDefinition();
				}
			} else {
				deployProcessDefinition();
			}

		} catch (

		Exception e) {
			e.printStackTrace();
		}
		
	}

	@Test
	public void complete(){
		engine.buildProcessEngine();
		showHis();
		engine.getTaskService().complete(util.getPropertyValue("taskId"));
		TaskEntity task = (TaskEntity) engine.getTaskService().createTaskQuery().processInstanceId(util.getPropertyValue("instanceId")).singleResult();
		if (task != null) {
			String taskId = task.getId();
			task = (TaskEntity) engine.getTaskService().createTaskQuery().processInstanceId(util.getPropertyValue("instanceId")).singleResult();
			if (task != null) {
				taskId = task.getId();
				util.setPropertiesValue("taskId", taskId);
			}
		}
		showHis();
	}
	
	@Test
	public void completeSpecialTask(){
		engine.buildProcessEngine();
		showHis();
		engine.getTaskService().complete("5918");
		showHis();
		engine.getTaskService().complete("5920");
		showHis();
		engine.getTaskService().complete("5922");
		showHis();
	}

	private void showHis() {
		List<HistoricActivityInstance> hiss = engine.getHistoryService().createHistoricActivityInstanceQuery().processInstanceId(util.getPropertyValue("instanceId")).list();
		log.info("------------------------------------------------------");
		for (HistoricActivityInstance h : hiss) {
			log.info("task:id:{},activitiId:{},activityName:{},assignee:{},endTime:{}",
					h.getTaskId(),h.getActivityId(),h.getActivityName(),h.getAssignee(),h.getEndTime());
		}
	}

	@Test
	public void getService() {
		// 准备流程定义，加载到测试用例中，必须有
		engine.buildProcessEngine();
		ProcessEngine processEngine = engine.getProcessEngine();
		RepositoryService repositoryService = processEngine.getRepositoryService();
		log.info(repositoryService.toString());
		RuntimeService runtimeService = processEngine.getRuntimeService();
		log.info(runtimeService.toString());
		TaskService taskService = processEngine.getTaskService();
		log.info(taskService.toString());
		IdentityService IdentityService = processEngine.getIdentityService();
		log.info(IdentityService.toString());
		FormService formService = processEngine.getFormService();
		log.info(formService.toString());
		HistoryService historyService = processEngine.getHistoryService();
		log.info(historyService.toString());
		ManagementService managementService = processEngine.getManagementService();
		log.info(managementService.toString());
		FilterService filterService = processEngine.getFilterService();
		log.info(filterService.toString());
		ExternalTaskService externalTaskService = processEngine.getExternalTaskService();
		log.info(externalTaskService.toString());
		CaseService caseService = processEngine.getCaseService();
		log.info(caseService.toString());
		DecisionService decisionService = processEngine.getDecisionService();
		log.info(decisionService.toString());
	}
}
