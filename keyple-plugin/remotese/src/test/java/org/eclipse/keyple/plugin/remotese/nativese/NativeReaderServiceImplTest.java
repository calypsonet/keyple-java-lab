package org.eclipse.keyple.plugin.remotese.nativese;

import org.eclipse.keyple.plugin.remotese.transport.DtoSender;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.junit.Before;
import org.mockito.Mock;

//@RunWith(MockitoJUnitRunner.class)
public class NativeReaderServiceImplTest {

    @Mock
    SeProxyService seProxyService;

    @Mock
    DtoSender dtoSender;

    NativeReaderServiceImpl nse;

    @Before
    public void Setup(){

        nse = new NativeReaderServiceImpl(dtoSender);

    }



}
