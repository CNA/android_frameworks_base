/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

#include "rsContext.h"
#include "rsScriptC.h"
#include "rsMatrix.h"
#include "utils/Timers.h"
#include "utils/StopWatch.h"

#include <GLES/gl.h>
#include <GLES/glext.h>

#include <bcc/bcc.h>

using namespace android;
using namespace android::renderscript;

#define GET_TLS()  Context::ScriptTLSStruct * tls = \
    (Context::ScriptTLSStruct *)pthread_getspecific(Context::gThreadTLSKey); \
    Context * rsc = tls->mContext; \
    ScriptC * sc = (ScriptC *) tls->mScript

ScriptC::ScriptC(Context *rsc) : Script(rsc) {
}

ScriptC::~ScriptC() {
    mRSC->mHal.funcs.script.destroy(mRSC, this);
}

void ScriptC::setupScript(Context *rsc) {
    mEnviroment.mStartTimeMillis
                = nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_MONOTONIC));

    for (uint32_t ct=0; ct < mHal.info.exportedVariableCount; ct++) {
        if (mSlots[ct].get() && !mTypes[ct].get()) {
            mTypes[ct].set(mSlots[ct]->getType());
        }

        if (!mTypes[ct].get())
            continue;
        void *ptr = NULL;
        if (mSlots[ct].get()) {
            ptr = mSlots[ct]->getPtr();
        }

        rsc->mHal.funcs.script.setGlobalBind(rsc, this, ct, ptr);
    }
}

const Allocation *ScriptC::ptrToAllocation(const void *ptr) const {
    //LOGE("ptr to alloc %p", ptr);
    if (!ptr) {
        return NULL;
    }
    for (uint32_t ct=0; ct < mHal.info.exportedVariableCount; ct++) {
        if (!mSlots[ct].get())
            continue;
        if (mSlots[ct]->getPtr() == ptr) {
            return mSlots[ct].get();
        }
    }
    LOGE("ScriptC::ptrToAllocation, failed to find %p", ptr);
    return NULL;
}

void ScriptC::setupGLState(Context *rsc) {
    if (mEnviroment.mFragmentStore.get()) {
        rsc->setProgramStore(mEnviroment.mFragmentStore.get());
    }
    if (mEnviroment.mFragment.get()) {
        rsc->setProgramFragment(mEnviroment.mFragment.get());
    }
    if (mEnviroment.mVertex.get()) {
        rsc->setProgramVertex(mEnviroment.mVertex.get());
    }
    if (mEnviroment.mRaster.get()) {
        rsc->setProgramRaster(mEnviroment.mRaster.get());
    }
}

uint32_t ScriptC::run(Context *rsc) {
    if (mHal.info.root == NULL) {
        rsc->setError(RS_ERROR_BAD_SCRIPT, "Attempted to run bad script");
        return 0;
    }

    setupGLState(rsc);
    setupScript(rsc);

    uint32_t ret = 0;

    if (rsc->props.mLogScripts) {
        LOGV("%p ScriptC::run invoking root,  ptr %p", rsc, mHal.info.root);
    }

    ret = rsc->mHal.funcs.script.invokeRoot(rsc, this);

    if (rsc->props.mLogScripts) {
        LOGV("%p ScriptC::run invoking complete, ret=%i", rsc, ret);
    }

    return ret;
}


void ScriptC::runForEach(Context *rsc,
                         const Allocation * ain,
                         Allocation * aout,
                         const void * usr,
                         const RsScriptCall *sc) {

    Context::PushState ps(rsc);

    setupGLState(rsc);
    setupScript(rsc);
    rsc->mHal.funcs.script.invokeForEach(rsc, this, ain, aout, usr, 0, sc);
}

void ScriptC::Invoke(Context *rsc, uint32_t slot, const void *data, uint32_t len) {
    if (slot >= mHal.info.exportedFunctionCount) {
        rsc->setError(RS_ERROR_BAD_SCRIPT, "Calling invoke on bad script");
        return;
    }
    setupScript(rsc);

    if (rsc->props.mLogScripts) {
        LOGV("%p ScriptC::Invoke invoking slot %i,  ptr %p", rsc, slot, this);
    }
    rsc->mHal.funcs.script.invokeFunction(rsc, this, slot, data, len);
}

ScriptCState::ScriptCState() {
}

ScriptCState::~ScriptCState() {
}

static void* symbolLookup(void* pContext, char const* name) {
    const ScriptCState::SymbolTable_t *sym;
    ScriptC *s = (ScriptC *)pContext;
    if (!strcmp(name, "__isThreadable")) {
      return (void*) s->mHal.info.isThreadable;
    } else if (!strcmp(name, "__clearThreadable")) {
      s->mHal.info.isThreadable = false;
      return NULL;
    }
    sym = ScriptCState::lookupSymbol(name);
    if (!sym) {
        sym = ScriptCState::lookupSymbolCL(name);
    }
    if (!sym) {
        sym = ScriptCState::lookupSymbolGL(name);
    }
    if (sym) {
        s->mHal.info.isThreadable &= sym->threadable;
        return sym->mPtr;
    }
    LOGE("ScriptC sym lookup failed for %s", name);
    return NULL;
}

#if 0
extern const char rs_runtime_lib_bc[];
extern unsigned rs_runtime_lib_bc_size;
#endif

bool ScriptC::runCompiler(Context *rsc,
                          const char *resName,
                          const char *cacheDir,
                          const uint8_t *bitcode,
                          size_t bitcodeLen) {

    //LOGE("runCompiler %p %p %p %p %p %i", rsc, this, resName, cacheDir, bitcode, bitcodeLen);

    rsc->mHal.funcs.script.init(rsc, this, resName, cacheDir, bitcode, bitcodeLen, 0, symbolLookup);

    mEnviroment.mFragment.set(rsc->getDefaultProgramFragment());
    mEnviroment.mVertex.set(rsc->getDefaultProgramVertex());
    mEnviroment.mFragmentStore.set(rsc->getDefaultProgramStore());
    mEnviroment.mRaster.set(rsc->getDefaultProgramRaster());

    rsc->mHal.funcs.script.invokeInit(rsc, this);

    for (size_t i=0; i < mHal.info.exportedPragmaCount; ++i) {
        const char * key = mHal.info.exportedPragmaKeyList[i];
        const char * value = mHal.info.exportedPragmaValueList[i];
        //LOGE("pragma %s %s", keys[i], values[i]);
        if (!strcmp(key, "version")) {
            if (!strcmp(value, "1")) {
                continue;
            }
            LOGE("Invalid version pragma value: %s\n", value);
            return false;
        }

        if (!strcmp(key, "stateVertex")) {
            if (!strcmp(value, "default")) {
                continue;
            }
            if (!strcmp(value, "parent")) {
                mEnviroment.mVertex.clear();
                continue;
            }
            LOGE("Unrecognized value %s passed to stateVertex", value);
            return false;
        }

        if (!strcmp(key, "stateRaster")) {
            if (!strcmp(value, "default")) {
                continue;
            }
            if (!strcmp(value, "parent")) {
                mEnviroment.mRaster.clear();
                continue;
            }
            LOGE("Unrecognized value %s passed to stateRaster", value);
            return false;
        }

        if (!strcmp(key, "stateFragment")) {
            if (!strcmp(value, "default")) {
                continue;
            }
            if (!strcmp(value, "parent")) {
                mEnviroment.mFragment.clear();
                continue;
            }
            LOGE("Unrecognized value %s passed to stateFragment", value);
            return false;
        }

        if (!strcmp(key, "stateStore")) {
            if (!strcmp(value, "default")) {
                continue;
            }
            if (!strcmp(value, "parent")) {
                mEnviroment.mFragmentStore.clear();
                continue;
            }
            LOGE("Unrecognized value %s passed to stateStore", value);
            return false;
        }
    }

    mSlots = new ObjectBaseRef<Allocation>[mHal.info.exportedVariableCount];
    mTypes = new ObjectBaseRef<const Type>[mHal.info.exportedVariableCount];

    return true;
}

namespace android {
namespace renderscript {

RsScript rsi_ScriptCCreate(Context *rsc,
                           const char *resName, const char *cacheDir,
                           const char *text, uint32_t len)
{
    ScriptC *s = new ScriptC(rsc);

    if (!s->runCompiler(rsc, resName, cacheDir, (uint8_t *)text, len)) {
        // Error during compile, destroy s and return null.
        delete s;
        return NULL;
    }

    s->incUserRef();
    return s;
}

}
}
