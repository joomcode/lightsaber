/*
 * Copyright 2022 SIA Joom
 *
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

package test_case_projects.external_setup_analyzer.provided_by_dependency_project

import com.joom.lightsaber.ProvidedBy
import test_case_projects.external_setup_analyzer.dependency_project.DependencyProjectModule
import javax.inject.Inject

@ProvidedBy(DependencyProjectModule::class)
class ProvidedDependency @Inject constructor()

