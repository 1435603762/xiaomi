/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.exception; // 定义了一个异常类，位于 net.micode.notes.gtask.exception 包下

public class ActionFailureException extends RuntimeException { // 定义了一个继承自 RuntimeException 的异常类

    private static final long serialVersionUID = 4425249765923293627L; // 定义一个序列化 ID，用于版本控制

    public ActionFailureException() { // 无参构造函数
        super(); // 调用父类 RuntimeException 的无参构造函数
    }

    public ActionFailureException(String paramString) { // 带参数构造函数，传入异常信息
        super(paramString); // 调用父类 RuntimeException 的带参数构造函数，将异常信息传给父类
    }

    public ActionFailureException(String paramString, Throwable paramThrowable) { // 带参数构造函数，传入异常信息和原始异常
        super(paramString, paramThrowable); // 调用父类 RuntimeException 的带参数构造函数，将异常信息和原始异常都传给父类
    }
}
