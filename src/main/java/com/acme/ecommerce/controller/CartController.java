package com.acme.ecommerce.controller;

import com.acme.ecommerce.domain.Product;
import com.acme.ecommerce.domain.ProductPurchase;
import com.acme.ecommerce.domain.Purchase;
import com.acme.ecommerce.domain.ShoppingCart;
import com.acme.ecommerce.exceptions.OutOfStockException;
import com.acme.ecommerce.exceptions.ProductNotFoundException;
import com.acme.ecommerce.service.ProductService;
import com.acme.ecommerce.service.PurchaseService;
import com.acme.ecommerce.web.FlashMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.math.BigDecimal;

@Controller
@RequestMapping("/cart")
@Scope("request")
public class CartController {
	final Logger logger = LoggerFactory.getLogger(CartController.class);
	
	@Autowired
	PurchaseService purchaseService;
	
	@Autowired
	private ProductService productService;
	
	@Autowired
	private ShoppingCart sCart;
	
	@Autowired
	private HttpSession session;
	
    @RequestMapping("")
    public String viewCart(Model model) {
    	logger.debug("Getting Product List");
    	logger.debug("Session ID = " + session.getId());
    	
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal = new BigDecimal(0);
    	
    	model.addAttribute("purchase", purchase);
    	if (purchase != null) {
    		for (ProductPurchase pp : purchase.getProductPurchases()) {
    			logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
    			subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
    		}
    		model.addAttribute("subTotal", subTotal);
    	} else {
    		logger.error("No purchases Found for session ID=" + session.getId());
    		return "redirect:/error";
    	}
        return "cart";
    }
    
    @RequestMapping(path="/add", method = RequestMethod.POST)
    public RedirectView addToCart(@ModelAttribute(value="productId") long productId, @ModelAttribute(value="quantity") int quantity, RedirectAttributes redirectAttributes) {
		boolean productAlreadyInCart = false;
    	RedirectView redirect = new RedirectView("/product/");
		redirect.setExposeModelAttributes(false);

		Product addProduct = productService.findById(productId);
		if (addProduct != null) {
			productService.isSufficientInventory(quantity, addProduct.getQuantity());
			logger.debug("Adding Product: " + addProduct.getId());

			Purchase purchase = sCart.getPurchase();
			if (purchase == null) {
				purchase = new Purchase();
				sCart.setPurchase(purchase);
			} else {
				for (ProductPurchase pp : purchase.getProductPurchases()) {
					if (pp.getProduct() != null) {
						if (pp.getProduct().getId().equals(productId)) {
							productAlreadyInCart = true;
							productService.isSufficientInventory(quantity + pp.getQuantity(), addProduct.getQuantity());
							pp.setQuantity(pp.getQuantity() + quantity);
							break;
						}
					}
				}
			}
			if (!productAlreadyInCart) {
				ProductPurchase newProductPurchase = new ProductPurchase();
				newProductPurchase.setProduct(addProduct);
				newProductPurchase.setQuantity(quantity);
				newProductPurchase.setPurchase(purchase);
				purchase.getProductPurchases().add(newProductPurchase);
			}
			logger.debug("Added " + quantity + " of " + addProduct.getName() + " to cart");
			sCart.setPurchase(purchaseService.save(purchase));

			redirectAttributes.addFlashAttribute("flash", new FlashMessage("'" + addProduct.getName() +
					"' was successfully added to cart!", FlashMessage.Status.SUCCESS));
		} else {
			logger.error("Attempt to add unknown product: " + productId);
			redirect.setUrl("/error");
		}

    	return redirect;
    }
 
    @RequestMapping(path="/update", method = RequestMethod.POST)
    public RedirectView updateCart(@ModelAttribute(value="productId") long productId, @ModelAttribute(value="newQuantity") int newQuantity, RedirectAttributes redirectAttributes) {
    	logger.debug("Updating Product: " + productId + " with Quantity: " + newQuantity);
		RedirectView redirect = new RedirectView("/cart");
		redirect.setExposeModelAttributes(false);
    	
    	Product updateProduct = productService.findById(productId);
		if (updateProduct != null) {
			productService.isSufficientInventory(newQuantity, updateProduct.getQuantity());
    		Purchase purchase = sCart.getPurchase();
    		if (purchase == null) {
    			logger.error("Unable to find shopping cart for update");
    			redirect.setUrl("/error");
    		} else {
    			for (ProductPurchase pp : purchase.getProductPurchases()) {
    				if (pp.getProduct() != null) {
    					if (pp.getProduct().getId().equals(productId)) {
    						if (newQuantity > 0) {
    							pp.setQuantity(newQuantity);
    							logger.debug("Updated " + updateProduct.getName() + " to " + newQuantity);
								redirectAttributes.addFlashAttribute("flash", new FlashMessage("Updated '" + updateProduct.getName() +
										"' quantity in cart to " + newQuantity + "!", FlashMessage.Status.SUCCESS));
    						} else {
    							purchase.getProductPurchases().remove(pp);
    							logger.debug("Removed " + updateProduct.getName() + " because quantity was set to " + newQuantity);
								redirectAttributes.addFlashAttribute("flash", new FlashMessage("Updated '" + updateProduct.getName() +
										"' quantity in cart to " + newQuantity + "!", FlashMessage.Status.SUCCESS));
    						}
    						break;
    					}
    				}
    			}
    		}
    		sCart.setPurchase(purchaseService.save(purchase));
    	} else {
    		logger.error("Attempt to update on non-existent product");
    		redirect.setUrl("/error");
    	}
    	
    	return redirect;
    }
    
    @RequestMapping(path="/remove", method = RequestMethod.POST)
    public RedirectView removeFromCart(@ModelAttribute(value="productId") long productId, RedirectAttributes redirectAttributes) {
    	logger.debug("Removing Product: " + productId);
		RedirectView redirect = new RedirectView("/cart");
		redirect.setExposeModelAttributes(false);
    	
    	Product updateProduct = productService.findById(productId);
    	if (updateProduct != null) {
    		Purchase purchase = sCart.getPurchase();
    		if (purchase != null) {
    			for (ProductPurchase pp : purchase.getProductPurchases()) {
    				if (pp.getProduct() != null) {
    					if (pp.getProduct().getId().equals(productId)) {
    						purchase.getProductPurchases().remove(pp);
   							logger.debug("Removed " + updateProduct.getName());
							redirectAttributes.addFlashAttribute("flash", new FlashMessage("Removed '" + updateProduct.getName() +
									"' from cart!", FlashMessage.Status.SUCCESS));
    						break;
    					}
    				}
    			}
    			purchase = purchaseService.save(purchase);
    			sCart.setPurchase(purchase);
    			if (purchase.getProductPurchases().isEmpty()) {
        	    	//if last item in cart redirect to product else return cart
        			redirect.setUrl("/product/");
        		}
    		} else {
    			logger.error("Unable to find shopping cart for update");
    			redirect.setUrl("/error");
    		}
    	} else {
    		logger.error("Attempt to update on non-existent product");
    		redirect.setUrl("/error");
    	}

    	return redirect;
    }
    
    @RequestMapping(path="/empty", method = RequestMethod.POST)
    public RedirectView emptyCart(RedirectAttributes redirectAttributes) {
    	RedirectView redirect = new RedirectView("/product/");
		redirect.setExposeModelAttributes(false);
    	
    	logger.debug("Emptying Cart");
    	Purchase purchase = sCart.getPurchase();
		if (purchase != null) {
			purchase.getProductPurchases().clear();
			sCart.setPurchase(purchaseService.save(purchase));
			redirectAttributes.addFlashAttribute("flash", new FlashMessage("Cart is empty!", FlashMessage.Status.SUCCESS));
		} else {
			logger.error("Unable to find shopping cart for update");
			redirect.setUrl("/error");
		}
		
    	return redirect;
    }

	@ExceptionHandler({OutOfStockException.class})
	public String outOfStockError(OutOfStockException e,
										HttpServletRequest request) {
		logger.debug("Insufficient stock of product");

		FlashMap outputFlashMap = RequestContextUtils.getOutputFlashMap(request);
		if (outputFlashMap != null){
			outputFlashMap.put("flash", new FlashMessage(e.getMessage(), FlashMessage.Status.FAILURE));
		}
		return "redirect:" + request.getHeader("referer");
	}

	@ResponseStatus(value= HttpStatus.NOT_FOUND	,
			reason="Product not found")
	@ExceptionHandler({ProductNotFoundException.class})
	public String productNotFoundError(Exception exception, Model model){

		logger.error("Product id not found!");

		model.addAttribute("exception", exception.getMessage());

		return "/error";
	}
}
