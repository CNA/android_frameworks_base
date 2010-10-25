/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include <SkPixelRef.h>
#include "ResourceCache.h"
#include "Caches.h"

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Resource cache
///////////////////////////////////////////////////////////////////////////////

void ResourceCache::logCache() {
    LOGD("ResourceCache: cacheReport:");
    for (size_t i = 0; i < mCache->size(); ++i) {
        ResourceReference* ref = mCache->valueAt(i);
        LOGD("  ResourceCache: mCache(%d): resource, ref = 0x%p, 0x%p",
                i, mCache->keyAt(i), mCache->valueAt(i));
        LOGD("  ResourceCache: mCache(%d): refCount, recycled, destroyed, type = %d, %d, %d, %d",
                i, ref->refCount, ref->recycled, ref->destroyed, ref->resourceType);
    }
}

ResourceCache::ResourceCache() {
    mCache = new KeyedVector<void *, ResourceReference *>();
}

ResourceCache::~ResourceCache() {
    delete mCache;
}

void ResourceCache::incrementRefcount(void* resource, ResourceType resourceType) {
    for (size_t i = 0; i < mCache->size(); ++i) {
        void* ref = mCache->valueAt(i);
    }
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL || mCache->size() == 0) {
        ref = new ResourceReference(resourceType);
        mCache->add(resource, ref);
    }
    ref->refCount++;
}

void ResourceCache::incrementRefcount(SkBitmap* bitmapResource) {
    bitmapResource->pixelRef()->safeRef();
    bitmapResource->getColorTable()->safeRef();
    incrementRefcount((void*)bitmapResource, kBitmap);
}

void ResourceCache::incrementRefcount(SkMatrix* matrixResource) {
    incrementRefcount((void*)matrixResource, kMatrix);
}

void ResourceCache::incrementRefcount(SkPaint* paintResource) {
    incrementRefcount((void*)paintResource, kPaint);
}

void ResourceCache::incrementRefcount(SkiaShader* shaderResource) {
    shaderResource->getSkShader()->safeRef();
    incrementRefcount((void*)shaderResource, kShader);
}

void ResourceCache::decrementRefcount(void* resource) {
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL) {
        // Should not get here - shouldn't get a call to decrement if we're not yet tracking it
        return;
    }
    ref->refCount--;
    if (ref->refCount == 0) {
        deleteResourceReference(resource, ref);
    }
}

void ResourceCache::decrementRefcount(SkBitmap* bitmapResource) {
    bitmapResource->pixelRef()->safeUnref();
    bitmapResource->getColorTable()->safeUnref();
    decrementRefcount((void*)bitmapResource);
}

void ResourceCache::decrementRefcount(SkiaShader* shaderResource) {
    shaderResource->getSkShader()->safeUnref();
    decrementRefcount((void*)shaderResource);
}

void ResourceCache::recycle(SkBitmap* resource) {
    if (mCache->indexOfKey(resource) < 0) {
        // not tracking this resource; just recycle the pixel data
        resource->setPixels(NULL, NULL);
        return;
    }
    recycle((void*) resource);
}

void ResourceCache::recycle(void* resource) {
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL) {
        // Should not get here - shouldn't get a call to recycle if we're not yet tracking it
        return;
    }
    ref->recycled = true;
    if (ref->refCount == 0) {
        deleteResourceReference(resource, ref);
    }
}

void ResourceCache::destructor(SkBitmap* resource) {
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL) {
        // If we're not tracking this resource, just delete it
        if (Caches::hasInstance()) {
            Caches::getInstance().textureCache.remove(resource);
        }
        delete resource;
        return;
    }
    ref->destroyed = true;
    if (ref->refCount == 0) {
        deleteResourceReference(resource, ref);
        return;
    }
}

void ResourceCache::destructor(SkMatrix* resource) {
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL) {
        // If we're not tracking this resource, just delete it
        delete resource;
        return;
    }
    ref->destroyed = true;
    if (ref->refCount == 0) {
        deleteResourceReference(resource, ref);
        return;
    }
}

void ResourceCache::destructor(SkPaint* resource) {
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL) {
        // If we're not tracking this resource, just delete it
        delete resource;
        return;
    }
    ref->destroyed = true;
    if (ref->refCount == 0) {
        deleteResourceReference(resource, ref);
        return;
    }
}

void ResourceCache::destructor(SkiaShader* resource) {
    ResourceReference* ref = mCache->indexOfKey(resource) >= 0 ? mCache->valueFor(resource) : NULL;
    if (ref == NULL) {
        // If we're not tracking this resource, just delete it
        if (Caches::hasInstance()) {
            Caches::getInstance().gradientCache.remove(resource->getSkShader());
        }
        delete resource;
        return;
    }
    ref->destroyed = true;
    if (ref->refCount == 0) {
        deleteResourceReference(resource, ref);
        return;
    }
}

void ResourceCache::deleteResourceReference(void* resource, ResourceReference* ref) {
    if (ref->recycled && ref->resourceType == kBitmap) {
        ((SkBitmap*) resource)->setPixels(NULL, NULL);
    }
    if (ref->destroyed) {
        switch (ref->resourceType) {
            case kBitmap:
            {
                SkBitmap* bitmap = (SkBitmap*)resource;
                if (Caches::hasInstance()) {
                    Caches::getInstance().textureCache.remove(bitmap);
                }
                delete bitmap;
            }
            break;
            case kMatrix:
                delete (SkMatrix*) resource;
                break;
            case kPaint:
                delete (SkPaint*) resource;
                break;
            case kShader:
                SkiaShader* shader = (SkiaShader*)resource;
                if (Caches::hasInstance()) {
                    Caches::getInstance().gradientCache.remove(shader->getSkShader());
                }
                delete shader;
                break;
        }
    }
    mCache->removeItem(resource);
    delete ref;
}

}; // namespace uirenderer
}; // namespace android
