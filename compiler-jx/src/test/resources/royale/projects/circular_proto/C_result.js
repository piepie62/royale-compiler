/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Generated by Apache Royale Compiler from C.as
 * C
 *
 * @fileoverview
 *
 * @suppress {missingRequire|checkTypes|accessControls}
 */

goog.provide('C');

goog.require('B');



/**
 * @constructor
 * @extends {B}
 */
C = function() {
  C.base(this, 'constructor');
};
goog.inherits(C, B);


/**
 * Prevent renaming of class. Needed for reflection.
 */
goog.exportSymbol('C', C);


/**
 * @export
 * @param {boolean} b
 * @return {number}
 */
C.a = function(b) {
  return F.a(false);
};


/**
 * Metadata
 *
 * @type {Object.<string, Array.<Object>>}
 */
C.prototype.ROYALE_CLASS_INFO = { names: [{ name: 'C', qName: 'C', kind: 'class' }] };



/**
 * Reflection
 *
 * @return {Object.<string, Function>}
 */
C.prototype.ROYALE_REFLECTION_INFO = function () {
  return {
    variables: function () {return {};},
    accessors: function () {return {};},
    methods: function () {
      return {
        'C': { type: '', declaredBy: 'C'},
        '|a': { type: 'int', declaredBy: 'C', parameters: function () { return [  { index: 1, type: 'Boolean', optional: false } ]; }}
      };
    }
  };
};