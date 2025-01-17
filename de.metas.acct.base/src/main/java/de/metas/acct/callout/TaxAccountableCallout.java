package de.metas.acct.callout;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import java.math.BigDecimal;
import java.util.Properties;

import org.compiere.model.I_C_ElementValue;
import org.compiere.model.I_C_Tax;
import org.compiere.model.I_C_ValidCombination;
import org.compiere.util.Env;

import de.metas.acct.api.AcctSchemaId;
import de.metas.acct.tax.ITaxAccountable;
import de.metas.acct.tax.ITaxAcctBL;
import de.metas.currency.CurrencyPrecision;
import de.metas.tax.api.ITaxBL;
import de.metas.util.Services;

/**
 * Callout for {@link ITaxAccountable} records
 * 
 * @author tsa
 * @task http://dewiki908/mediawiki/index.php/08351_Automatikibuchung_Steuer_in_Hauptbuchjournal_%28106598648165%29
 */
/* package */class TaxAccountableCallout
{
	// NOTE: no status fields are allowed because it's assume this is stateless

	/**
	 * Called when Tax Base Account is set.
	 * 
	 * @param taxAccountable
	 */
	public void onTaxBaseAccount(final ITaxAccountable taxAccountable)
	{
		final BigDecimal taxTotalAmt = taxAccountable.getTaxTotalAmt();

		final I_C_Tax tax = getTaxOrNull(taxAccountable.getTaxBase_Acct());
		taxAccountable.setC_Tax(tax);
		taxAccountable.setTaxTotalAmt(taxTotalAmt);
	}

	/**
	 * Called when TaxBaseAmt is changed.
	 * 
	 * Sets TaxAmt and TaxTotalAmt.
	 * 
	 * @param taxAccountable
	 */
	public void onTaxBaseAmt(final ITaxAccountable taxAccountable)
	{
		final I_C_Tax tax = taxAccountable.getC_Tax();
		if (tax == null)
		{
			return;
		}

		//
		// Calculate Tax Amt
		final BigDecimal taxBaseAmt = taxAccountable.getTaxBaseAmt();
		final boolean taxIncluded = false;
		final CurrencyPrecision precision = taxAccountable.getPrecision();
		final ITaxBL taxBL = Services.get(ITaxBL.class);
		final BigDecimal taxAmt = taxBL.calculateTax(tax, taxBaseAmt, taxIncluded, precision.toInt());

		final BigDecimal totalAmt = taxBaseAmt.add(taxAmt);

		taxAccountable.setTaxAmt(taxAmt);
		taxAccountable.setTaxTotalAmt(totalAmt);
	}

	/**
	 * Called when TaxAmt is changed.
	 * 
	 * Sets TaxTotalAmt.
	 * 
	 * @param taxAccountable
	 */
	public void onTaxAmt(final ITaxAccountable taxAccountable)
	{
		final BigDecimal taxBaseAmt = taxAccountable.getTaxBaseAmt();
		final BigDecimal taxAmt = taxAccountable.getTaxAmt();
		final BigDecimal totalAmt = taxBaseAmt.add(taxAmt);
		taxAccountable.setTaxTotalAmt(totalAmt);
	}

	/**
	 * Called when TaxTotalAmt is changed.
	 * 
	 * Sets TaxAmt and TaxBaseAmt.
	 * 
	 * @param taxAccountable
	 */
	public void onTaxTotalAmt(final ITaxAccountable taxAccountable)
	{
		final I_C_Tax tax = taxAccountable.getC_Tax();
		if (tax == null)
		{
			return;
		}

		//
		// Calculate TaxAmt
		final BigDecimal taxTotalAmt = taxAccountable.getTaxTotalAmt();
		final boolean taxIncluded = true;
		final CurrencyPrecision precision = taxAccountable.getPrecision();
		final ITaxBL taxBL = Services.get(ITaxBL.class);
		final BigDecimal taxAmt = taxBL.calculateTax(tax, taxTotalAmt, taxIncluded, precision.toInt());

		final BigDecimal taxBaseAmt = taxTotalAmt.subtract(taxAmt);

		taxAccountable.setTaxAmt(taxAmt);
		taxAccountable.setTaxBaseAmt(taxBaseAmt);
	}

	/**
	 * Called when C_Tax_ID is changed.
	 * 
	 * Sets Tax_Acct, TaxAmt.
	 * 
	 * @param taxAccountable
	 */
	public void onC_Tax_ID(final ITaxAccountable taxAccountable)
	{
		final int taxAcctType;
		if (taxAccountable.isAccountSignDR())
		{
			taxAcctType = ITaxAcctBL.ACCTTYPE_TaxCredit;
			// taxAcctType = ITaxAcctBL.ACCTTYPE_TaxExpense; // used for booking services tax
		}
		else if (taxAccountable.isAccountSignCR())
		{
			taxAcctType = ITaxAcctBL.ACCTTYPE_TaxDue;
		}
		else
		{
			return;
		}

		//
		// Set DR/CR Tax Account
		final int taxId = taxAccountable.getC_Tax_ID();
		if (taxId > 0)
		{
			final Properties ctx = Env.getCtx();
			final AcctSchemaId acctSchemaId = taxAccountable.getAcctSchemaId();
			final ITaxAcctBL taxAcctBL = Services.get(ITaxAcctBL.class);
			final I_C_ValidCombination taxAcct = taxAcctBL.getC_ValidCombination(ctx, taxId, acctSchemaId, taxAcctType);
			taxAccountable.setTax_Acct(taxAcct);
		}
		else
		{
			taxAccountable.setTax_Acct(null);
		}

		//
		// Set TaxAmt based on TaxBaseAmt and C_Tax_ID
		onTaxBaseAmt(taxAccountable);
	}

	private final I_C_Tax getTaxOrNull(final I_C_ValidCombination accountVC)
	{
		if (accountVC == null)
		{
			return null;
		}

		final I_C_ElementValue account = accountVC.getAccount();
		if (account == null)
		{
			return null;
		}

		if (!account.isAutoTaxAccount())
		{
			return null;
		}

		final I_C_Tax tax = account.getC_Tax();
		if (tax == null || tax.getC_Tax_ID() <= 0)
		{
			return null;
		}
		return tax;
	}

}
