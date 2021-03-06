/*
    Copyright 1996-2008 Ariba, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    $Id: //ariba/platform/ui/widgets/ariba/ui/widgets/HtmlInclude.java#4 $
*/

package ariba.ui.widgets;

import ariba.ui.aribaweb.core.AWConcreteApplication;
import ariba.ui.aribaweb.core.AWComponent;
import ariba.ui.aribaweb.core.AWBindingNames;
import ariba.ui.aribaweb.util.AWResource;
import ariba.ui.aribaweb.util.AWUtil;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

public final class HtmlInclude extends AWComponent
{

    private static Pattern EventPattern =
        Pattern.compile("(?i)on(click|mousein|mouseover|mousedown|mouseup|keydown|keypress|blur|focus)");

    public String htmlString ()
    {
        String htmlString = null;
        String filename = (String)valueForBinding(AWBindingNames.filename);
        if (filename != null) {
            AWResource resource = resourceManager().resourceNamed(filename);
            if (resource != null) {
                htmlString = (String)resource.object();
                if ((htmlString == null) || (AWConcreteApplication.IsRapidTurnaroundEnabled && resource.hasChanged())) {
                    InputStream inputStream = resource.inputStream();
                    inputStream = new BufferedInputStream(inputStream);
                    htmlString = AWUtil.stringWithContentsOfInputStream(inputStream,
                        booleanValueForBinding("expectEncoding"));
                    AWUtil.close(inputStream);
                    if (htmlString == null) {
                        htmlString = "";
                    }
                    else {
                        // See AWGenericElement._TranslatedBindings
                        htmlString = EventPattern.matcher(htmlString).replaceAll("x$1");
                    }
                    resource.setObject(htmlString);
                }
            }
        }
        return htmlString;
    }
}
