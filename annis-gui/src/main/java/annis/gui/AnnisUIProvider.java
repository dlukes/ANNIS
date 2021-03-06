/*
 * Copyright 2015 Corpuslinguistic working group Humboldt University Berlin.
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
package annis.gui;

import com.vaadin.server.UIClassSelectionEvent;
import com.vaadin.server.UIProvider;
import com.vaadin.ui.UI;

/**
 *
 * @author Thomas Krause <krauseto@hu-berlin.de>
 */
public class AnnisUIProvider extends UIProvider
{

  @Override
  public Class<? extends UI> getUIClass(UIClassSelectionEvent event)
  {
    String path = event.getRequest().getPathInfo();
    if(path != null && path.startsWith(EmbeddedVisUI.URL_PREFIX))
    {
      return EmbeddedVisUI.class;
    }
    else
    {
      return AnnisUI.class;
    }
  }
  
}
