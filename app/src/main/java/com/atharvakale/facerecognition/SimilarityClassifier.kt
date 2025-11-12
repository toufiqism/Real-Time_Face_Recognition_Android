/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.atharvakale.facerecognition

interface SimilarityClassifier {
    /** An immutable result returned by a Classifier describing what was recognized. */
    class Recognition(
        /** A unique identifier for what has been recognized. Specific to the class, not the instance of the object. */
        val id: String,
        /** Display name for the recognition. */
        val title: String,
        val distance: Float?
    ) {
        var extra: Any? = null

        override fun toString(): String {
            val resultString = buildString {
                if (id.isNotEmpty()) {
                    append("[$id] ")
                }
                if (title.isNotEmpty()) {
                    append("$title ")
                }
                if (distance != null) {
                    append("(%.1f%%) ".format(distance * 100.0f))
                }
            }
            return resultString.trim()
        }
    }
}

