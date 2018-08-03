/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmily.tcc.core.service.handler;

import com.hmily.tcc.common.bean.context.TccTransactionContext;
import com.hmily.tcc.common.bean.entity.Participant;
import com.hmily.tcc.common.bean.entity.TccTransaction;
import com.hmily.tcc.common.enums.TccActionEnum;
import com.hmily.tcc.core.cache.TccTransactionCacheManager;
import com.hmily.tcc.core.service.HmilyTransactionHandler;
import com.hmily.tcc.core.service.executor.HmilyTransactionExecutor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Participant Handler.
 *
 * @author xiaoyu
 */
@Component
public class ParticipantHmilyTransactionHandler implements HmilyTransactionHandler {

    private final HmilyTransactionExecutor hmilyTransactionExecutor;

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantHmilyTransactionHandler.class);

    @Autowired
    public ParticipantHmilyTransactionHandler(final HmilyTransactionExecutor hmilyTransactionExecutor) {
        this.hmilyTransactionExecutor = hmilyTransactionExecutor;
    }

    @Override
    public Object handler(final ProceedingJoinPoint point, final TccTransactionContext context) throws Throwable {
        TccTransaction tccTransaction = null;
        TccTransaction currentTransaction;
        switch (TccActionEnum.acquireByCode(context.getAction())) {
            case TRYING:
                try {
                    tccTransaction = hmilyTransactionExecutor.beginParticipant(context, point);
                    final Object proceed = point.proceed();

                    String targetClass = tccTransaction.getTargetClass();
                    String methodName = tccTransaction.getTargetMethod();
                    //参与者方法
                    LOGGER.debug("try 阶段目标 className :{}, methodName :{} " ,targetClass,methodName);
                    List<Participant> participants = tccTransaction.getParticipants();
                    participants.stream().forEach(participant -> {

                    LOGGER.debug("try 阶段合作者的 className:{} ,methodName :{}",participant.getConfirmTccInvocation().getTargetClass().getName()
                    ,participant.getConfirmTccInvocation().getMethodName());
                    });

                    tccTransaction.setStatus(TccActionEnum.TRYING.getCode());
                    //update log status to try
                    hmilyTransactionExecutor.updateStatus(tccTransaction);
                    return proceed;
                } catch (Throwable throwable) {
                    //if exception ,delete log.
                    hmilyTransactionExecutor.deleteTransaction(tccTransaction);
                    throw throwable;
                }
            case CONFIRMING:
                currentTransaction = TccTransactionCacheManager.getInstance().getTccTransaction(context.getTransId());
                //confirming 阶段中
                String targetClass = currentTransaction.getTargetClass();
                String methodName  = currentTransaction.getTargetMethod();
                LOGGER.debug("confirming 阶段目标 className :{}, methodName :{} " ,targetClass,methodName);

                List<Participant> participants = currentTransaction.getParticipants();
                participants.stream().forEach(participant ->{
                    LOGGER.debug("conform 阶段合作者的 className:{} ,methodName :{}",participant.getConfirmTccInvocation().getTargetClass().getName()
                            ,participant.getConfirmTccInvocation().getMethodName());
                });

                hmilyTransactionExecutor.confirm(currentTransaction);
                break;
            case CANCELING:
                currentTransaction = TccTransactionCacheManager.getInstance().getTccTransaction(context.getTransId());
                hmilyTransactionExecutor.cancel(currentTransaction);
                break;
            default:
                break;
        }
        Method method = ((MethodSignature) (point.getSignature())).getMethod();
        return getDefaultValue(method.getReturnType());
    }

    private Object getDefaultValue(final Class type) {
        if (boolean.class.equals(type)) {
            return false;
        } else if (byte.class.equals(type)) {
            return 0;
        } else if (short.class.equals(type)) {
            return 0;
        } else if (int.class.equals(type)) {
            return 0;
        } else if (long.class.equals(type)) {
            return 0;
        } else if (float.class.equals(type)) {
            return 0;
        } else if (double.class.equals(type)) {
            return 0;
        }
        return null;
    }
}
