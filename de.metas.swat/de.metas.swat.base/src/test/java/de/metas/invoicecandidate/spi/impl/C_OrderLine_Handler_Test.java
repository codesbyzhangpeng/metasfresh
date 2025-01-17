package de.metas.invoicecandidate.spi.impl;

/*
 * #%L
 * de.metas.swat.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.agg.key.IAggregationKeyBuilder;
import org.adempiere.warehouse.WarehouseId;
import org.compiere.model.I_C_DocType;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.X_C_DocType;
import org.compiere.util.Env;
import org.junit.Before;
import org.junit.Test;

import ch.qos.logback.classic.Level;
import de.metas.acct.api.IProductAcctDAO;
import de.metas.bpartner.service.IBPartnerBL;
import de.metas.bpartner.service.impl.BPartnerBL;
import de.metas.document.engine.DocStatus;
import de.metas.invoicecandidate.AbstractICTestSupport;
import de.metas.invoicecandidate.InvoiceCandidatesTestHelper;
import de.metas.invoicecandidate.api.impl.HeaderAggregationKeyBuilder;
import de.metas.invoicecandidate.model.I_C_BPartner;
import de.metas.invoicecandidate.model.I_C_ILCandHandler;
import de.metas.invoicecandidate.model.I_C_Invoice_Candidate;
import de.metas.invoicecandidate.spi.InvoiceCandidateGenerateRequest;
import de.metas.logging.LogManager;
import de.metas.order.invoicecandidate.C_OrderLine_Handler;
import de.metas.organization.OrgId;
import de.metas.tax.api.ITaxBL;
import de.metas.tax.api.TaxCategoryId;
import de.metas.user.UserRepository;
import de.metas.util.Services;
import mockit.Expectations;
import mockit.Mocked;

public class C_OrderLine_Handler_Test extends AbstractICTestSupport
{
	private final C_OrderLine_Handler oLHandler = new C_OrderLine_Handler();
	private I_C_ILCandHandler handler;
	private final IAggregationKeyBuilder<I_C_Invoice_Candidate> headerAggregationKeyBuilder = new HeaderAggregationKeyBuilder();

	// task 07442
	@Mocked
	protected IProductAcctDAO productAcctDAO;

	// task 07442
	@Mocked
	protected ITaxBL taxBL;

	@Before
	public void init()
	{
		final Properties ctx = Env.getCtx();
		Env.setContext(ctx, Env.CTXNAME_AD_Client_ID, 1);
		Env.setContext(ctx, Env.CTXNAME_AD_Language, "de_CH");

		handler = InterfaceWrapperHelper.create(Env.getCtx(), I_C_ILCandHandler.class, ITrx.TRXNAME_None);
		// current DB structure for OLHandler
		handler.setC_ILCandHandler_ID(540001);
		handler.setClassname(C_OrderLine_Handler.class.getName());
		handler.setName("Auftragszeilen");
		handler.setTableName(I_C_OrderLine.Table_Name);

		InterfaceWrapperHelper.save(handler);

		// configure olHandler
		oLHandler.setHandlerRecord(handler);

		LogManager.setLevel(Level.DEBUG);

		Services.registerService(IBPartnerBL.class, new BPartnerBL(new UserRepository()));
	}

	@Test
	public void testSimilarAggregationKeys()
	{
		final I_C_BPartner bp = InterfaceWrapperHelper.create(bpartner("Test1"), I_C_BPartner.class);
		InterfaceWrapperHelper.save(bp);

		final I_C_Order order1 = order("1");
		order1.setAD_Org_ID(orgId.getRepoId());
		order1.setM_Warehouse_ID(warehouseId.getRepoId());
		order1.setBill_BPartner_ID(bp.getC_BPartner_ID());
		InterfaceWrapperHelper.save(order1);

		final I_C_OrderLine oL1 = orderLine("1");
		oL1.setAD_Org_ID(orgId.getRepoId());
		oL1.setC_Order(order1);
		oL1.setM_Product_ID(productId.getRepoId());
		InterfaceWrapperHelper.save(oL1);

		setUpActivityAndTaxRetrieval(order1, oL1);

		final I_C_Order order2 = order("2");
		order2.setAD_Org_ID(orgId.getRepoId());
		order2.setM_Warehouse_ID(warehouseId.getRepoId());
		order2.setBill_BPartner_ID(bp.getC_BPartner_ID());
		InterfaceWrapperHelper.save(order2);

		final I_C_OrderLine oL2 = orderLine("2");
		oL2.setAD_Org_ID(orgId.getRepoId());
		oL2.setC_Order(order2);
		oL2.setM_Product_ID(productId.getRepoId());
		InterfaceWrapperHelper.save(oL2);

		setUpActivityAndTaxRetrieval(order2, oL2);

		final List<I_C_Invoice_Candidate> iCands1 = oLHandler.createCandidatesFor(InvoiceCandidateGenerateRequest.of(oLHandler, oL1)).getC_Invoice_Candidates();
		final List<I_C_Invoice_Candidate> iCands2 = oLHandler.createCandidatesFor(InvoiceCandidateGenerateRequest.of(oLHandler, oL2)).getC_Invoice_Candidates();

		updateInvalidCandidates();

		assertThat(iCands1.size(), comparesEqualTo(1));
		assertThat(iCands2.size(), comparesEqualTo(1));

		final I_C_Invoice_Candidate ic1 = iCands1.get(0);
		final I_C_Invoice_Candidate ic2 = iCands2.get(0);

		ic1.setC_Order(order1);
		InterfaceWrapperHelper.save(ic1);
		ic2.setC_Order(order2);
		InterfaceWrapperHelper.save(ic2);

		final String key1 = headerAggregationKeyBuilder.buildKey(ic1);
		final String key2 = headerAggregationKeyBuilder.buildKey(ic2);

		assertEquals(key1, key2);
	}

	private void setUpActivityAndTaxRetrieval(final I_C_Order order1, final I_C_OrderLine oL1)
	{
		Services.registerService(IProductAcctDAO.class, productAcctDAO);
		Services.registerService(ITaxBL.class, taxBL);

		// @formatter:off
		new Expectations()
		{{
				productAcctDAO.retrieveActivityForAcct(
						clientId,
						orgId,
						productId);
				minTimes = 0;
				result = activityId;

				productAcctDAO.retrieveActivityForAcct(
						withNotEqual(clientId),
						withNotEqual(orgId),
						withNotEqual(productId));
				minTimes = 0;
				result = null;

				final Properties ctx = Env.getCtx();
				taxBL.getTax(
						ctx
						, order1
						, (TaxCategoryId)null
						, oL1.getM_Product_ID()
						, order1.getDatePromised()
						, OrgId.ofRepoId(order1.getAD_Org_ID())
						, WarehouseId.ofRepoId(order1.getM_Warehouse_ID())
						, order1.getC_BPartner_Location_ID()
						, order1.isSOTrx());
				minTimes = 0;
				result = 3;
		}};
		// @formatter:on
	}

	@Test
	public void testCreateMissingCandidates()
	{

		final I_C_DocType auftrag = docType(X_C_DocType.DOCBASETYPE_SalesOrder, null);
		auftrag.setC_DocType_ID(1);
		InterfaceWrapperHelper.save(auftrag);

		final I_C_DocType bestellung = docType(X_C_DocType.DOCBASETYPE_PurchaseOrder, null);
		bestellung.setC_DocType_ID(2);
		InterfaceWrapperHelper.save(bestellung);

		final I_C_BPartner bp = InterfaceWrapperHelper.create(bpartner("Test1"), I_C_BPartner.class);
		InterfaceWrapperHelper.save(bp);

		// Taken into consideration: valid Auftrag for creating invoice cand
		final I_C_Order order1 = order("1");
		order1.setAD_Org_ID(orgId.getRepoId());
		order1.setM_Warehouse_ID(warehouseId.getRepoId());
		order1.setIsSOTrx(true);
		order1.setC_DocType_ID(1);
		order1.setBill_BPartner_ID(bp.getC_BPartner_ID());
		order1.setDocStatus(DocStatus.Completed.getCode());
		InterfaceWrapperHelper.save(order1);

		final I_C_OrderLine oL1 = orderLine("1");
		oL1.setAD_Org_ID(orgId.getRepoId());
		oL1.setC_Order(order1);
		oL1.setM_Product_ID(productId.getRepoId());
		oL1.setQtyOrdered(new BigDecimal(5));
		oL1.setQtyInvoiced(new BigDecimal(0));
		InterfaceWrapperHelper.save(oL1);

		setUpActivityAndTaxRetrieval(order1, oL1);

		// Taken into consideration: valid Bestellung for creating invoice cand
		final I_C_Order order2 = order("2");
		order2.setAD_Org_ID(orgId.getRepoId());
		order2.setM_Warehouse_ID(warehouseId.getRepoId());
		order2.setIsSOTrx(false);
		order2.setC_DocType_ID(2);
		order2.setBill_BPartner_ID(bp.getC_BPartner_ID());
		order2.setDocStatus(DocStatus.Completed.getCode());
		InterfaceWrapperHelper.save(order2);

		final I_C_OrderLine oL2 = orderLine("2");
		oL2.setAD_Org_ID(orgId.getRepoId());
		oL2.setC_Order(order2);
		oL2.setM_Product_ID(productId.getRepoId());
		oL2.setQtyOrdered(new BigDecimal(5));
		oL2.setQtyInvoiced(new BigDecimal(0));
		InterfaceWrapperHelper.save(oL2);

		setUpActivityAndTaxRetrieval(order2, oL2);

		// Not taken into consideration: not completed.
		final I_C_Order order3 = order("3");
		order3.setAD_Org_ID(orgId.getRepoId());
		order3.setM_Warehouse_ID(warehouseId.getRepoId());
		order3.setIsSOTrx(false);
		order3.setC_DocType_ID(2);
		order3.setBill_BPartner_ID(bp.getC_BPartner_ID());
		order3.setDocStatus(DocStatus.WaitingConfirmation.getCode());
		InterfaceWrapperHelper.save(order3);

		final I_C_OrderLine oL3 = orderLine("3");
		oL3.setAD_Org_ID(orgId.getRepoId());
		oL3.setC_Order(order3);
		oL3.setM_Product_ID(productId.getRepoId());
		oL3.setQtyOrdered(new BigDecimal(5));
		oL3.setQtyInvoiced(new BigDecimal(0));
		InterfaceWrapperHelper.save(oL3);

		setUpActivityAndTaxRetrieval(order3, oL3);

		// Not taken into consideration: invoiced and ordered quantities are equal
		final I_C_Order order4 = order("4");
		order4.setAD_Org_ID(orgId.getRepoId());
		order4.setM_Warehouse_ID(warehouseId.getRepoId());
		order4.setIsSOTrx(true);
		order4.setC_DocType_ID(1);
		order4.setBill_BPartner_ID(bp.getC_BPartner_ID());
		order4.setDocStatus(DocStatus.Completed.getCode());
		InterfaceWrapperHelper.save(order4);

		final I_C_OrderLine oL4 = orderLine("4");
		oL4.setAD_Org_ID(orgId.getRepoId());
		oL4.setC_Order(order4);
		oL4.setM_Product_ID(productId.getRepoId());
		oL4.setQtyOrdered(new BigDecimal(5));
		oL4.setQtyInvoiced(new BigDecimal(5));
		InterfaceWrapperHelper.save(oL4);

		setUpActivityAndTaxRetrieval(order4, oL4);

		final List<I_C_Invoice_Candidate> candidates = InvoiceCandidatesTestHelper.createMissingCandidates(oLHandler, 5);

		assertEquals(2, candidates.size());

		final I_C_Invoice_Candidate cand1 = candidates.get(0);
		final I_C_Invoice_Candidate cand2 = candidates.get(1);

		// Check that we create both SO and PO candidates
		assertEquals(cand1.isSOTrx(), !cand2.isSOTrx());

		// Check that the candidates are for the correct order lines
		assertTrue(cand1.isSOTrx() ? cand1.getC_OrderLine_ID() == oL1.getC_OrderLine_ID() : cand2.getC_OrderLine_ID() == oL1.getC_OrderLine_ID());
		assertTrue(cand2.isSOTrx() ? cand1.getC_OrderLine_ID() == oL2.getC_OrderLine_ID() : cand2.getC_OrderLine_ID() == oL2.getC_OrderLine_ID());

	}
}
