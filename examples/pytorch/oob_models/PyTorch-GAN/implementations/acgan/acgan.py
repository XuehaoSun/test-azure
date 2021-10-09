import argparse
import os
import numpy as np
import math

import torchvision.transforms as transforms
from torchvision.utils import save_image

from torch.utils.data import DataLoader
from torchvision import datasets
from torch.autograd import Variable

import torch.nn as nn
import torch.nn.functional as F
import torch
import time

os.makedirs("images", exist_ok=True)

parser = argparse.ArgumentParser()
parser.add_argument("--n_epochs", type=int, default=200, help="number of epochs of training")
parser.add_argument("--batch_size", type=int, default=64, help="size of the batches")
parser.add_argument("--lr", type=float, default=0.0002, help="adam: learning rate")
parser.add_argument("--b1", type=float, default=0.5, help="adam: decay of first order momentum of gradient")
parser.add_argument("--b2", type=float, default=0.999, help="adam: decay of first order momentum of gradient")
parser.add_argument("--n_cpu", type=int, default=8, help="number of cpu threads to use during batch generation")
parser.add_argument("--latent_dim", type=int, default=100, help="dimensionality of the latent space")
parser.add_argument("--n_classes", type=int, default=10, help="number of classes for dataset")
parser.add_argument("--img_size", type=int, default=32, help="size of each image dimension")
parser.add_argument("--channels", type=int, default=1, help="number of image channels")
parser.add_argument("--sample_interval", type=int, default=400, help="interval between image sampling")
parser.add_argument('--inference', action='store_true', default=False)
parser.add_argument('--num-iterations', default=10000, type=int)
parser.add_argument('--ipex', action='store_true', default=False)
parser.add_argument('--precision', default='float32', help='Precision, "float32" or "bfloat16"')
parser.add_argument('--channels_last', type=int, default=1, help='use channels last format')
parser.add_argument('--tune', action='store_true', default=False, help="Tune")
parser.add_argument('--profile', action='store_true', help="Trigger profile on current topology.")

opt = parser.parse_args()
print(opt)

cuda = True if torch.cuda.is_available() else False
FloatTensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
LongTensor = torch.cuda.LongTensor if cuda else torch.LongTensor

def weights_init_normal(m):
    classname = m.__class__.__name__
    if classname.find("Conv") != -1:
        torch.nn.init.normal_(m.weight.data, 0.0, 0.02)
    elif classname.find("BatchNorm2d") != -1:
        torch.nn.init.normal_(m.weight.data, 1.0, 0.02)
        torch.nn.init.constant_(m.bias.data, 0.0)

def test_quant_fx_graph(model):
    from torch.quantization import default_qconfig
    from torch.quantization.quantize_fx import prepare_fx, convert_fx, fuse_fx
    model = model.eval()
    qconfig_dict = {"": default_qconfig}
    model = prepare_fx(model, qconfig_dict)
    model = convert_fx(model)
    print(type(model).__name__, " fx mode quantization Pass!")
    return model

class DataLoader(object):
    def __init__(self, data=None, batch_size=1):
        self.data = data
        self.batch_size = batch_size
    def __iter__(self):
        yield self.data, self.data[1]


class Generator(nn.Module):
    def __init__(self):
        super(Generator, self).__init__()

        self.label_emb = nn.Embedding(opt.n_classes, opt.latent_dim)

        self.init_size = opt.img_size // 4  # Initial size before upsampling
        self.l1 = nn.Sequential(nn.Linear(opt.latent_dim, 128 * self.init_size ** 2))

        self.conv_blocks = nn.Sequential(
            nn.BatchNorm2d(128),
            nn.Upsample(scale_factor=2),
            nn.Conv2d(128, 128, 3, stride=1, padding=1),
            nn.BatchNorm2d(128, 0.8),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Upsample(scale_factor=2),
            nn.Conv2d(128, 64, 3, stride=1, padding=1),
            nn.BatchNorm2d(64, 0.8),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Conv2d(64, opt.channels, 3, stride=1, padding=1),
            nn.Tanh(),
        )

    def forward(self, noise, labels):
        gen_input = torch.mul(self.label_emb(labels), noise)
        out = self.l1(gen_input)
        out = out.view(out.shape[0], 128, self.init_size, self.init_size)
        img = self.conv_blocks(out)
        return img


class Discriminator(nn.Module):
    def __init__(self):
        super(Discriminator, self).__init__()

        def discriminator_block(in_filters, out_filters, bn=True):
            """Returns layers of each discriminator block"""
            block = [nn.Conv2d(in_filters, out_filters, 3, 2, 1), nn.LeakyReLU(0.2, inplace=True), nn.Dropout2d(0.25)]
            if bn:
                block.append(nn.BatchNorm2d(out_filters, 0.8))
            return block

        self.conv_blocks = nn.Sequential(
            *discriminator_block(opt.channels, 16, bn=False),
            *discriminator_block(16, 32),
            *discriminator_block(32, 64),
            *discriminator_block(64, 128),
        )

        # The height and width of downsampled image
        ds_size = opt.img_size // 2 ** 4

        # Output layers
        self.adv_layer = nn.Sequential(nn.Linear(128 * ds_size ** 2, 1), nn.Sigmoid())
        self.aux_layer = nn.Sequential(nn.Linear(128 * ds_size ** 2, opt.n_classes), nn.Softmax())

    def forward(self, img):
        out = self.conv_blocks(img)
        out = out.view(out.shape[0], -1)
        validity = self.adv_layer(out)
        label = self.aux_layer(out)

        return validity, label


# Loss functions
adversarial_loss = torch.nn.BCELoss()
auxiliary_loss = torch.nn.CrossEntropyLoss()

# Initialize generator and discriminator
generator = Generator()
discriminator = Discriminator()

if cuda:
    generator.cuda()
    discriminator.cuda()
    adversarial_loss.cuda()
    auxiliary_loss.cuda()

# Initialize weights
generator.apply(weights_init_normal)
discriminator.apply(weights_init_normal)

# Configure data loader
os.makedirs("../../data/mnist", exist_ok=True)
dataloader = torch.utils.data.DataLoader(
    datasets.MNIST(
        "../../data/mnist",
        train=True,
        download=True,
        transform=transforms.Compose(
            [transforms.Resize(opt.img_size), transforms.ToTensor(), transforms.Normalize([0.5], [0.5])]
        ),
    ),
    batch_size=opt.batch_size,
    shuffle=True,
)

# from torch.fx import symbolic_trace
# traced = symbolic_trace(discriminator)
# torch.save(traced, "traced_acgan-discriminator.pth")
# print('saved trace')
# try:
#     q_model = torch.jit.script(discriminator.eval())
# except:
#     try:
#         for input, _ in dataloader:
#             q_model = torch.jit.trace(discriminator.eval(), input)
#             break
#     except:
#         logger.info("This model can't convert to Script model")
# q_model.save("jit_acgan-discriminator.pth")
# print('saved jit')
# print(0/0)
# from torch.fx import symbolic_trace
# traced = symbolic_trace(generator)
# torch.save(traced, "traced_acgan-generator.pth")
# print('saved trace')
# try:
#     q_model = torch.jit.script(generator.eval())
# except:
#     try:
#         for input,label in dataloader:
#             print(label)
#             q_model = torch.jit.trace(generator.eval(), (input, label))
#             break
#     except:
#         logger.info("This model can't convert to Script model")
# q_model.save("jit_acgan-generator.pth")
# print('saved jit')
# print(0/0)

# Optimizers
optimizer_G = torch.optim.Adam(generator.parameters(), lr=opt.lr, betas=(opt.b1, opt.b2))
optimizer_D = torch.optim.Adam(discriminator.parameters(), lr=opt.lr, betas=(opt.b1, opt.b2))

FloatTensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
LongTensor = torch.cuda.LongTensor if cuda else torch.LongTensor

def generate(netG, batchsize, device):
    if opt.tune:
        from neural_compressor.experimental import Quantization, common
        quantizer = Quantization(('../../conf.yaml'))
        n_row = 10
        fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
        labels = np.array([num for _ in range(n_row) for num in range(n_row)])
        labels = Variable(LongTensor(labels))
        dataset = (fixed_noise, labels)
        calib_dataloader = DataLoader(dataset)
        quantizer.calib_dataloader = calib_dataloader
        quantizer.model = common.Model(netG)
        q_model = quantizer()
        q_model.save("saved_results")
        return
    else:
        if opt.precision == 'int8':
            from neural_compressor.utils.pytorch import load
            netG = load("saved_results", netG)

    n_row = 10
    fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
#     # Get labels ranging from 0 to n_classes for n rows
    labels = np.array([num for _ in range(n_row) for num in range(n_row)])
    labels = Variable(LongTensor(labels))

    # fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (1, opt.latent_dim)))) #Variable(FloatTensor(np.random.normal(0, 1, (img_shape[0], opt.latent_dim))))
    dry_run = 5
    netG.eval()

    if opt.channels_last:
        netG_oob, fixed_noise_oob = netG, fixed_noise
        netG_oob = netG_oob.to(memory_format=torch.channels_last)
        try:
            fixed_noise_oob = fixed_noise_oob.to(memory_format=torch.channels_last)
        except:
            print("Input NHWC failed! Use normal input.")
        netG, fixed_noise = netG_oob, fixed_noise_oob
    else:
        netG = netG.to(device=device)
        fixed_noise = fixed_noise.to(device=device)
    # if opt.jit:
    #     netG = torch.jit.trace(netG, fixed_noise)
    tic = 0
    
    with torch.no_grad():
        for i in range(dry_run + opt.num_iterations):
            if i == dry_run:
                tic = time.time()
                fake = netG(fixed_noise, labels)
            elif i > dry_run:
                if opt.profile:
                    if opt.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            with torch.autograd.profiler.profile() as prof:
                                fake = netG(fixed_noise, labels)
                    else:
                        with torch.autograd.profiler.profile() as prof:
                            fake = netG(fixed_noise, labels)
                    table_res = prof.key_averages().table(sort_by="cpu_time_total")
                    timeline_path = "./timeline_logs/" + opt.arch + "/"
                    if not os.path.exists(timeline_path):
                        os.makedirs(timeline_path)
                    save_profile_result(timeline_path + torch.backends.quantized.engine + "_result_average.xlsx", table_res)
                else:
                    if opt.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            fake = netG(fixed_noise, labels)
                    else:
                        fake = netG(fixed_noise, labels)
            else:
                fake = netG(fixed_noise, labels)


    toc = time.time() - tic
    print("Throughput: %.2f images/sec, batchsize: %d" % ((opt.num_iterations*batchsize)/toc, batchsize))
    print("Latency: %.2f ms" % (1000*toc/opt.num_iterations))


if opt.inference:
    print("----------------Generation benchmarking---------------")
    generate(generator, opt.batch_size, device=torch.device('cpu'))
    import sys
    sys.exit(0)

# def sample_image(n_row, batches_done):
#     """Saves a grid of generated digits ranging from 0 to n_classes"""
#     # Sample noise
#     z = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
#     # Get labels ranging from 0 to n_classes for n rows
#     labels = np.array([num for _ in range(n_row) for num in range(n_row)])
#     labels = Variable(LongTensor(labels))
#     gen_imgs = generator(z, labels)
#     save_image(gen_imgs.data, "images/%d.png" % batches_done, nrow=n_row, normalize=True)


# # ----------
# #  Training
# # ----------

# for epoch in range(opt.n_epochs):
#     for i, (imgs, labels) in enumerate(dataloader):

#         batch_size = imgs.shape[0]

#         # Adversarial ground truths
#         valid = Variable(FloatTensor(batch_size, 1).fill_(1.0), requires_grad=False)
#         fake = Variable(FloatTensor(batch_size, 1).fill_(0.0), requires_grad=False)

#         # Configure input
#         real_imgs = Variable(imgs.type(FloatTensor))
#         labels = Variable(labels.type(LongTensor))

#         # -----------------
#         #  Train Generator
#         # -----------------

#         optimizer_G.zero_grad()

#         # Sample noise and labels as generator input
#         z = Variable(FloatTensor(np.random.normal(0, 1, (batch_size, opt.latent_dim))))
#         gen_labels = Variable(LongTensor(np.random.randint(0, opt.n_classes, batch_size)))

#         # Generate a batch of images
#         gen_imgs = generator(z, gen_labels)

#         # Loss measures generator's ability to fool the discriminator
#         validity, pred_label = discriminator(gen_imgs)
#         g_loss = 0.5 * (adversarial_loss(validity, valid) + auxiliary_loss(pred_label, gen_labels))

#         g_loss.backward()
#         optimizer_G.step()

#         # ---------------------
#         #  Train Discriminator
#         # ---------------------

#         optimizer_D.zero_grad()

#         # Loss for real images
#         real_pred, real_aux = discriminator(real_imgs)
#         d_real_loss = (adversarial_loss(real_pred, valid) + auxiliary_loss(real_aux, labels)) / 2

#         # Loss for fake images
#         fake_pred, fake_aux = discriminator(gen_imgs.detach())
#         d_fake_loss = (adversarial_loss(fake_pred, fake) + auxiliary_loss(fake_aux, gen_labels)) / 2

#         # Total discriminator loss
#         d_loss = (d_real_loss + d_fake_loss) / 2

#         # Calculate discriminator accuracy
#         pred = np.concatenate([real_aux.data.cpu().numpy(), fake_aux.data.cpu().numpy()], axis=0)
#         gt = np.concatenate([labels.data.cpu().numpy(), gen_labels.data.cpu().numpy()], axis=0)
#         d_acc = np.mean(np.argmax(pred, axis=1) == gt)

#         d_loss.backward()
#         optimizer_D.step()

#         print(
#             "[Epoch %d/%d] [Batch %d/%d] [D loss: %f, acc: %d%%] [G loss: %f]"
#             % (epoch, opt.n_epochs, i, len(dataloader), d_loss.item(), 100 * d_acc, g_loss.item())
#         )
#         batches_done = epoch * len(dataloader) + i
#         if batches_done % opt.sample_interval == 0:
#             sample_image(n_row=10, batches_done=batches_done)
