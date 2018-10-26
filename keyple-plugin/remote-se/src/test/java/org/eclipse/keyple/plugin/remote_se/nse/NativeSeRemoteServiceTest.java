package org.eclipse.keyple.plugin.remote_se.nse;

import org.eclipse.keyple.plugin.remote_se.transport.DtoSender;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

//@RunWith(MockitoJUnitRunner.class)
public class NativeSeRemoteServiceTest {

    @Mock
    SeProxyService seProxyService;

    @Mock
    DtoSender dtoSender;

    NativeSeRemoteService nse;

    @Before
    public void Setup(){

        nse = new NativeSeRemoteService(dtoSender);

    }



}
